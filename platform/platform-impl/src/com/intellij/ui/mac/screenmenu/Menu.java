// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.screenmenu;

import com.intellij.CommonBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SimpleTimer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.ui.mac.MacMenuSettings;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext", "unused"})
public class Menu extends MenuItem {
  private static final boolean USE_STUB = Boolean.getBoolean("jbScreenMenuBar.useStubItem"); // just for tests/experiments
  private static final int CLOSE_DELAY = Integer.getInteger("jbScreenMenuBar.closeDelay", 500); // in milliseconds
  private static Boolean IS_ENABLED = null;
  private static Menu ourAppMenu = null;
  private final List<MenuItem> myItems = new ArrayList<>();
  private final List<MenuItem> myBuffer = new ArrayList<>();
  private Runnable myOnOpen;
  private Runnable myOnClose; // we assume that can run it only on EDT (to change swing components)
  private Component myComponent;
  private long myOpenTimeMs = 0; // used to collect statistic
  private volatile boolean myIsOpened = false;

  long[] myCachedPeers;

  public Menu(String title) {
    setTitle(title);
  }

  public final boolean isAnyChildOpened() {
    for (MenuItem item : myItems) {
      if (item instanceof Menu && ((Menu)item).myIsOpened) {
        return true;
      }
    }
    return false;
  }

  public boolean isOpened() {
    return myIsOpened;
  }
  public long getOpenTimeMs() { return myOpenTimeMs; }

  private Menu() { }

  // AppMenu is the first menu item (with title = application name) which is filled by OS
  public static Menu getAppMenu() {
    if (ourAppMenu == null) {
      ourAppMenu = new Menu();
      // returns retained pointer
      long nsMenu = nativeGetAppMenu();
      ourAppMenu.nativePeer = ourAppMenu.nativeAttachMenu(nsMenu);
      ourAppMenu.isInHierarchy = true;
    }
    return ourAppMenu;
  }

  private static String removeMnemonic(@Nls String src) {
    if (src == null)
      return "";

    TextWithMnemonic txt = TextWithMnemonic.parse(src);
    return txt.getText();
  }

  public static void renameAppMenuItems() {
    // NOTE: Application menu (i.e. first menu item with text = app name) for java application is loaded from
    // system framework 'JavaVM' (see NSApplicationAWT::finishLaunching). After execution of [NSBundle loadNibFile...]
    // we will have loaded app menu with hardcoded english (not internationalized) strings:
    // About %@
    // Preferences...
    // Services
    // Hide %@
    // Hide Others
    // Show All
    // Quit %@
    // And then %@ is replaced with $CFBundleName. Since strings are constant we will find menu item just by it's title.

    List<String> replace = new ArrayList<>(7);
    String bundleName = getBundleName();
    if (bundleName == null || bundleName.isEmpty()) {
      ApplicationNamesInfo names = ApplicationNamesInfo.getInstance();
      bundleName = names.getProductName();
    }

    replace.add("About.*");
    replace.add(removeMnemonic(ActionsBundle.message("action.About.text")) + " " + bundleName);

    // NOTE: Check For Updates is installed via Foundation from MacAppProvider
    replace.add("Check for Updates...");
    replace.add(removeMnemonic(ActionsBundle.message("action.CheckForUpdate.text")));

    //macOS 13.0 Ventura uses Settings instead of Preferences. See IDEA-300314
    String replacement = SystemInfo.isMacOSVentura ?
      removeMnemonic(CommonBundle.message("action.settings.macOS.ventura")):
      removeMnemonic(CommonBundle.message("action.settings.mac"));
    replace.add("Preferences.*"); // this replacement will be applied only on old OSX systems
    replace.add(replacement);
    replace.add("Settings.*");  // this replacement will be applied only on last OSX systems
    replace.add(replacement);

    replace.add("Services");
    replace.add(removeMnemonic(CommonBundle.message("action.appmenu.services")));

    replace.add("Hide " + bundleName);
    replace.add(removeMnemonic(CommonBundle.message("action.appmenu.hide_ide") + " " + bundleName));

    replace.add("Hide Others");
    replace.add(removeMnemonic(CommonBundle.message("action.appmenu.hide_others")));

    replace.add("Show All");
    replace.add(removeMnemonic(CommonBundle.message("action.appmenu.show_all")));

    replace.add("Quit.*");
    replace.add(removeMnemonic(CommonBundle.message("action.appmenu.quit") + " " + bundleName));

    nativeRenameAppMenuItems(ArrayUtilRt.toStringArray(replace));
  }

  public void setOnOpen(@NotNull Component component, @NotNull Runnable fillMenuProcedure) {
    this.myOnOpen = fillMenuProcedure;
    this.myComponent = component;
  }

  public void setOnClose(Component component, Runnable onClose) {
    this.myOnClose = onClose;
    this.myComponent = component;
  }

  @Override
  public void propertyChange(PropertyChangeEvent e) {
    @NonNls String propertyName = e.getPropertyName();
    if (Presentation.PROP_TEXT.equals(propertyName) || Presentation.PROP_DESCRIPTION.equals(propertyName)) {
      setTitle(presentation.getText());
    }
  }

  public void setTitle(String label) {
    ensureNativePeer();
    if (label == null) {
      label = "";
    }
    myTitle = label;
    nativeSetTitle(nativePeer, label, isInHierarchy);
  }

  // Search for sub-item by title (regexp) in native NSMenu peer
  // Returns the index of first child with matched title.
  public int findIndexByTitle(String re) {
    if (re == null || re.isEmpty()) return -1;

    ensureNativePeer();
    return nativeFindIndexByTitle(nativePeer, re);
  }

  // Search for sub-item by title (regexp) in native NSMenu peer
  // Returns the first child with matched title.
  // NOTE: Always creates java-wrapper for native NSMenuItem (that must be disposed manually)
  public synchronized MenuItem findItemByTitle(String re) {
    if (re == null || re.isEmpty()) return null;

    ensureNativePeer();
    long child = nativeFindItemByTitle(nativePeer, re); // returns retained pointer
    return child == 0 ? null : new MenuItem(child);
  }

  @SuppressWarnings("SSBasedInspection")
  synchronized void disposeChildren(int delayMs) {
    if (delayMs <= 0) {
      for (MenuItem item : myItems) {
        item.dispose();
      }
    }
    else {
      // Schedule to dispose later.
      // NOTE: Can be invoked in any thread, not only EDT
      final ArrayList<MenuItem> copy = new ArrayList<>(myItems);
      SimpleTimer.getInstance().setUp(() -> {
        for (MenuItem item : copy) {
          item.dispose();
        }
      }, delayMs);
    }
    myItems.clear();
  }

  @Override
  public synchronized void dispose() {
    disposeChildren(0);
    myCachedPeers = null;
    super.dispose();
  }

  public void beginFill() {
    for (MenuItem item : myBuffer) {
      if (item != null) {
        Disposer.dispose(item);
      }
    }
    myBuffer.clear();
  }

  public @Nullable MenuItem add(@Nullable MenuItem item) {
    myBuffer.add(item);
    return item;
  }

  public synchronized void endFill(boolean onAppKit) {
    disposeChildren(0);

    if (myBuffer.isEmpty()) {
      return;
    }

    myCachedPeers = new long[myBuffer.size()];
    //System.err.println("refill with " + newItemsPeers.length + " items");
    for (int c = 0; c < myBuffer.size(); ++c) {
      MenuItem menuItem = myBuffer.get(c);
      if (menuItem != null) {
        menuItem.ensureNativePeer();
        myCachedPeers[c] = menuItem.nativePeer;
        //System.err.printf("\t0x%X\n", newItemsPeers[c]);
        myItems.add(menuItem);
        menuItem.isInHierarchy = true;
      }
      else {
        myCachedPeers[c] = 0;
      }
    }
    myBuffer.clear();

    refillImpl(onAppKit);
  }

  synchronized void refillImpl(boolean onAppKit) {
    ensureNativePeer();
    if (myCachedPeers != null) {
      nativeRefill(nativePeer, myCachedPeers, onAppKit);
    }
  }

  public synchronized void endFill() {
    endFill(true);
  }

  public synchronized void add(MenuItem item, int position, boolean onAppKit) {
    if (position < 0) return;

    ensureNativePeer();
    item.ensureNativePeer();
    nativeInsertItem(nativePeer, item.nativePeer, position, onAppKit);
    myItems.add(item);
    // TODO: fix position inside myItems !!!
  }

  @Override
  synchronized void ensureNativePeer() {
    if (nativePeer == 0) {
      nativePeer = nativeCreateMenu();
    }
  }

  public void menuNeedsUpdate() {
    // Called on AppKit when the menu opening
    myIsOpened = true;
    if (myOnOpen == null) {
      return;
    }

    myOpenTimeMs = System.currentTimeMillis();
    if (USE_STUB) {
      // NOTE: must add stub item when the menu opens (otherwise AppKit considers it as empty, and we can't fill it later)
      MenuItem stub = new MenuItem();
      myItems.add(stub);
      stub.isInHierarchy = true;

      ensureNativePeer();
      stub.ensureNativePeer();
      nativeAddItem(nativePeer, stub.nativePeer, false/*already on AppKit thread*/);

      ApplicationManager.getApplication().invokeLater(() -> {
        beginFill();
        myOnOpen.run();
        endFill(true);
      });
    }
    else {
      beginFill();
      invokeWithLWCToolkit(myOnOpen, myComponent, true);
      endFill(false/*already on AppKit thread*/);
    }
  }

  public void menuWillOpen() {
    // Called on AppKit when a menu opening
    if (!myIsOpened) {
      // When a user opens some menu at second time (without focus lost), apple doesn't call menuNeedsUpdate for
      // this menu (but always calls menuWillOpen) and for all submenus. It causes problems like IDEA-319117.
      // So detect this case and call menuNeedsUpdate() "manually".
      // NOTE: unfortunately modifying menu here can cause unstable behavior, see IDEA-315910.
      getLogger().debug("menuNeedsUpdate wasn't called for '" + myTitle + "', will do it now");
      menuNeedsUpdate();
    }
  }

  public void invokeMenuClosing() {
    // Called on AppKit when the menu closed
    myIsOpened = false;

    // When a user selects item of a system menu (under macOS),
    // AppKit sometimes generates such sequence: CloseParentMenu -> PerformItemAction
    // So we can destroy menu-item before item's action performed, and because of that action will not be executed.
    // Defer clearing to avoid this problem.
    disposeChildren(CLOSE_DELAY);

    // NOTE: don't clear native hierarchy here (see IDEA-315910)
    // It will be done when a menu opens next time.

    if (myOnClose != null) {
      invokeWithLWCToolkit(myOnClose, myComponent, false);
    }
  }

  //
  // Native methods
  //

  // Creates native peer (wrapper for NSMenu with corresponding NSMenuItem).
  // User must dealloc it via nativeDispose after usage.
  // Can be invoked from any thread.
  private native long nativeCreateMenu();

  // Creates native peer (wrapper for NSMenu) with existing NSMenu (must be already retained)
  // User must dealloc it via nativeDispose after usage.
  // Can be invoked from any thread.
  private native long nativeAttachMenu(long nsMenu);

  // Find methods
  // Always performs on AppKit thread with waiting for a result
  private native long nativeFindItemByTitle(long menuPtr, String re); // NOTE: returns retained pointer

  private native int nativeFindIndexByTitle(long menuPtr, String re);

  // Modification methods
  // If a menu was created but wasn't added into any parent menu, then all setters can be invoked from any thread.
  private native void nativeSetTitle(long menuPtr, String title, boolean onAppKit);

  private native void nativeAddItem(long menuPtr, long itemPtr/*MenuItem OR Menu*/, boolean onAppKit);

  private native void nativeInsertItem(long menuPtr, long itemPtr/*MenuItem OR Menu*/, int position, boolean onAppKit);

  // Refill menu.
  // If menuPtr == null then refills sharedApplication.mainMenu with new items (except AppMenu, i.e., first item of mainMenu)
  native void nativeRefill(long menuPtr, long[] newItems, boolean onAppKit);

  private static native void nativeInitClass();

  // NOTE: returns retained pointer
  private static native long nativeGetAppMenu();

  private static native void nativeRenameAppMenuItems(String[] replacements);

  //
  // Static API
  //

  public static boolean isJbScreenMenuEnabled() {
    if (IS_ENABLED != null) {
      return IS_ENABLED;
    }

    IS_ENABLED = false;

    if (!MacMenuSettings.isJbSystemMenu) {
      return false;
    }

    if (Boolean.getBoolean("apple.laf.useScreenMenuBar")) {
      getLogger().info("apple.laf.useScreenMenuBar==true, default screen menu implementation will be used");
      return false;
    }

    @SuppressWarnings("SpellCheckingInspection")
    Path lib = PathManager.findBinFile("libmacscreenmenu64.dylib");
    try {
      System.load(Objects.requireNonNull(lib).toString());
      nativeInitClass();
      // create and dispose a native object (just for to test)
      Menu test = new Menu("test");
      test.ensureNativePeer();
      Disposer.dispose(test);
    }
    catch (Throwable e) {
      // default screen menu implementation will be used
      getLogger().warn("can't load menu library: " + lib + ", exception: " + e.getMessage());
      return false;
    }

    IS_ENABLED = true;
    getLogger().info("use new ScreenMenuBar implementation");
    return true;
  }

  private static MethodHandle invokeAndWait;
  private static MethodHandle invokeLater;

  static @Nullable MethodHandle getToolkitInvokeMethod(boolean wait) {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    try {
      if (wait) {
        if (invokeAndWait == null) {
          invokeAndWait = lookup.findStatic(getToolkitClass(),
                                            "invokeAndWait",
                                            MethodType.methodType(void.class, Runnable.class, Component.class, boolean.class,
                                                                  int.class));
        }
        return invokeAndWait;
      }
      else {
        if (invokeLater == null) {
          invokeLater = lookup.findStatic(getToolkitClass(),
                                          "invokeLater",
                                          MethodType.methodType(void.class, Runnable.class, Component.class));
        }
        return invokeLater;
      }
    }
    catch (ClassNotFoundException e) {
      //noinspection SpellCheckingInspection
      getLogger().warn("can't find sun.lwawt.macosx.LWCToolkit, screen menu won't be filled");
      return null;
    }
    catch (IllegalAccessException | NoSuchMethodException e) {
      //noinspection SpellCheckingInspection
      getLogger().warn("can't find sun.lwawt.macosx.LWCToolkit method, screen menu won't be filled", e);
      return null;
    }
  }

  private static @NotNull Class<?> getToolkitClass() throws ClassNotFoundException {
    //noinspection SpellCheckingInspection
    return Class.forName("sun.lwawt.macosx.LWCToolkit");
  }

  private static void invokeWithLWCToolkit(Runnable r, Component invoker, boolean wait) {
    MethodHandle invokeMethod = getToolkitInvokeMethod(wait);
    if (invokeMethod == null) {
      return;
    }

    try {
      if (wait) {
        invokeMethod.invoke(r, invoker, true, -1);
      }
      else {
        invokeMethod.invoke(r, invoker);
      }
    }
    catch (Throwable e) {
      // suppress InvocationTargetException as in openjdk implementation (see com.apple.laf.ScreenMenu.java)
      getLogger().warn("invokeWithLWCToolkit.invokeAndWait: " + e);
    }
  }

  private static @NotNull Logger getLogger() {
    return Logger.getInstance(Menu.class);
  }

  private static String getBundleName() {
    String bundleName;
    final ID nativePool = Foundation.invoke("NSAutoreleasePool", "new");
    try {
      final ID bundle = Foundation.invoke("NSBundle", "mainBundle");
      final ID dict = Foundation.invoke(bundle, "infoDictionary");
      final ID nsBundleName = Foundation.invoke(dict, "objectForKey:", Foundation.nsString("CFBundleName"));
      bundleName = Foundation.toStringViaUTF8(nsBundleName);
    }
    finally {
      Foundation.invoke(nativePool, "release");
    }
    return bundleName;
  }
}

