// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.UIUtil;
import com.jediterm.terminal.TerminalCopyPasteHandler;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.TerminalPanel;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class JBTerminalPanel extends TerminalPanel implements FocusListener, TerminalSettingsListener, Disposable {
  private static final Logger LOG = Logger.getInstance(JBTerminalPanel.class);
  private static final @NonNls String[] ACTIONS_TO_SKIP = new String[]{
    "ActivateTerminalToolWindow",
    "ActivateProjectToolWindow",
    "ActivateFavoritesToolWindow",
    "ActivateFindToolWindow",
    "ActivateRunToolWindow",
    "ActivateDebugToolWindow",
    "ActivateProblemsViewToolWindow",
    "ActivateTODOToolWindow",
    "ActivateStructureToolWindow",
    "ActivateHierarchyToolWindow",
    "ActivateServicesToolWindow",
    "ActivateCommitToolWindow",
    "ActivateVersionControlToolWindow",
    "HideActiveWindow",
    "HideAllWindows",

    "NextWindow",
    "PreviousWindow",
    "NextProjectWindow",
    "PreviousProjectWindow",

    "ShowBookmarks",
    "GotoBookmark0",
    "GotoBookmark1",
    "GotoBookmark2",
    "GotoBookmark3",
    "GotoBookmark4",
    "GotoBookmark5",
    "GotoBookmark6",
    "GotoBookmark7",
    "GotoBookmark8",
    "GotoBookmark9",

    "GotoAction",
    "GotoFile",
    "GotoClass",
    "GotoSymbol",

    "Vcs.Push",

    "ShowSettings",
    "RecentFiles",
    "Switcher",

    "ResizeToolWindowLeft",
    "ResizeToolWindowRight",
    "ResizeToolWindowUp",
    "ResizeToolWindowDown",
    "MaximizeToolWindow"
  };
  private static final int MIN_FONT_SIZE = 8;

  private final TerminalEventDispatcher myEventDispatcher = new TerminalEventDispatcher();
  private final JBTerminalSystemSettingsProviderBase mySettingsProvider;
  private final TerminalEscapeKeyListener myEscapeKeyListener;
  private final List<Consumer<KeyEvent>> myPreKeyEventConsumers = new CopyOnWriteArrayList<>();

  private List<AnAction> myActionsToSkip;

  public JBTerminalPanel(@NotNull JBTerminalSystemSettingsProviderBase settingsProvider,
                         @NotNull TerminalTextBuffer backBuffer,
                         @NotNull StyleState styleState) {
    super(settingsProvider, backBuffer, styleState);

    mySettingsProvider = settingsProvider;

    addFocusListener(this);

    mySettingsProvider.addListener(this);
    myEscapeKeyListener = new TerminalEscapeKeyListener(this);
  }

  private boolean skipKeyEvent(@NotNull KeyEvent e) {
    return skipAction(e, myActionsToSkip);
  }

  private static boolean skipAction(@NotNull KeyEvent e, @Nullable List<? extends AnAction> actionsToSkip) {
    if (actionsToSkip != null) {
      final KeyboardShortcut eventShortcut = new KeyboardShortcut(KeyStroke.getKeyStrokeForEvent(e), null);
      for (AnAction action : actionsToSkip) {
        for (Shortcut sc : action.getShortcutSet().getShortcuts()) {
          if (sc.isKeyboard() && sc.startsWith(eventShortcut)) {
            if (!Registry.is("terminal.Ctrl-E.opens.RecentFiles.popup", false) &&
                IdeActions.ACTION_RECENT_FILES.equals(ActionManager.getInstance().getId(action))) {
              if (e.getModifiersEx() == InputEvent.CTRL_DOWN_MASK && e.getKeyCode() == KeyEvent.VK_E) {
                return false;
              }
            }
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public void handleKeyEvent(@NotNull KeyEvent e) {
    if (SystemInfo.isMac && SystemInfo.isJavaVersionAtLeast(11, 0, 0)) {
      // Workaround for https://youtrack.jetbrains.com/issue/JBR-1098
      // sun.lwawt.macosx.CPlatformResponder.mapNsCharsToCompatibleWithJava converts 0x0003 (NSEnterCharacter) to 0x000a
      if (e.getKeyChar() == KeyEvent.VK_ENTER && e.getKeyCode() == KeyEvent.VK_C && e.getModifiersEx() == InputEvent.CTRL_DOWN_MASK) {
        LOG.debug("Fixing Ctrl+C");
        e.setKeyChar((char)3);
      }
    }
    myEscapeKeyListener.handleKeyEvent(e);
    for (Consumer<KeyEvent> preKeyEventConsumer : myPreKeyEventConsumers) {
      preKeyEventConsumer.accept(e);
    }
    if (!e.isConsumed()) {
      super.handleKeyEvent(e);
    }
  }

  public void addPreKeyEventHandler(@NotNull Consumer<KeyEvent> preKeyEventHandler) {
    myPreKeyEventConsumers.add(preKeyEventHandler);
  }

  @Override
  protected void setupAntialiasing(Graphics graphics) {
    UIUtil.setupComposite((Graphics2D)graphics);
    UISettings.setupAntialiasing(graphics);
  }

  @NotNull
  @Override
  protected TerminalCopyPasteHandler createCopyPasteHandler() {
    return new IdeTerminalCopyPasteHandler();
  }

  @Override
  protected void drawImage(Graphics2D gfx, BufferedImage image, int x, int y, ImageObserver observer) {
    UIUtil.drawImage(gfx, image, x, y, observer);
  }

  @Override
  protected void drawImage(Graphics2D g, BufferedImage image, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2) {
    drawImage(g, image, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
  }

  public static void drawImage(Graphics g,
                               Image image,
                               int dx1,
                               int dy1,
                               int dx2,
                               int dy2,
                               int sx1,
                               int sy1,
                               int sx2,
                               int sy2,
                               ImageObserver observer) {
    if (image instanceof JBHiDPIScaledImage) {
      final Graphics2D newG = (Graphics2D)g.create(0, 0, image.getWidth(observer), image.getHeight(observer));
      newG.scale(0.5, 0.5);
      Image img = ((JBHiDPIScaledImage)image).getDelegate();
      if (img == null) {
        img = image;
      }
      newG.drawImage(img, 2 * dx1, 2 * dy1, 2 * dx2, 2 * dy2, sx1 * 2, sy1 * 2, sx2 * 2, sy2 * 2, observer);
      newG.scale(1, 1);
      newG.dispose();
    }
    else {
      g.drawImage(image, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer);
    }
  }

  @Override
  protected boolean isRetina() {
    return UIUtil.isRetina();
  }

  @Override
  protected BufferedImage createBufferedImage(int width, int height) {
    return ImageUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB);
  }


  @Override
  public void focusGained(FocusEvent event) {
    if (mySettingsProvider.overrideIdeShortcuts()) {
      myActionsToSkip = setupActionsToSkip();
      myEventDispatcher.register();
    }
    else {
      myActionsToSkip = null;
      myEventDispatcher.unregister();
    }

    if (GeneralSettings.getInstance().isSaveOnFrameDeactivation()) {
      ApplicationManager.getApplication().invokeLater(() -> FileDocumentManager.getInstance().saveAllDocuments(), ModalityState.NON_MODAL);
    }
  }

  @NotNull
  private static List<AnAction> setupActionsToSkip() {
    List<AnAction> res = new ArrayList<>();
    ActionManager actionManager = ActionManager.getInstance();
    for (String actionId : ACTIONS_TO_SKIP) {
      AnAction action = actionManager.getAction(actionId);
      if (action != null) {
        res.add(action);
      }
    }
    return res;
  }

  @Override
  public void focusLost(FocusEvent event) {
    myActionsToSkip = null;
    myEventDispatcher.unregister();
    SaveAndSyncHandler.getInstance().scheduleRefresh();
  }

  @Override
  protected Font getFontToDisplay(char c, TextStyle style) {
    FontInfo fontInfo = fontForChar(c, style.hasOption(TextStyle.Option.BOLD) ? Font.BOLD : Font.PLAIN);
    return fontInfo.getFont();
  }

  public FontInfo fontForChar(final char c, @JdkConstants.FontStyle int style) {
    return ComplementaryFontsRegistry.getFontAbleToDisplay(c, style, mySettingsProvider.getColorsScheme().getConsoleFontPreferences(), null);
  }

  @Override
  public void fontChanged() {
    reinitFontAndResize();
  }

  @Override
  public void dispose() {
    super.dispose();
    mySettingsProvider.removeListener(this);
  }

  @Override
  protected void processMouseWheelEvent(MouseWheelEvent e) {
    if (EditorSettingsExternalizable.getInstance().isWheelFontChangeEnabled() && EditorUtil.isChangeFontSize(e)) {
      int newFontSize = (int)mySettingsProvider.getTerminalFontSize() - e.getWheelRotation();
      if (newFontSize >= MIN_FONT_SIZE) {
        mySettingsProvider.getColorsScheme().setConsoleFontSize(newFontSize);
        mySettingsProvider.fireFontChanged();
      }
      return;
    }
    super.processMouseWheelEvent(e);
  }

  /**
   * Adds "Override IDE shortcuts" terminal feature allowing terminal to process all the key events.
   * Without own IdeEventQueue.EventDispatcher, terminal won't receive key events corresponding to IDE action shortcuts.
   */
  private class TerminalEventDispatcher implements IdeEventQueue.EventDispatcher {

    private boolean myRegistered = false;

    @Override
    public boolean dispatch(@NotNull AWTEvent e) {
      return e instanceof KeyEvent && dispatchKeyEvent((KeyEvent)e);
    }

    private boolean dispatchKeyEvent(@NotNull KeyEvent e) {
      if (!skipKeyEvent(e)) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Consuming " + KeyStroke.getKeyStrokeForEvent(e) + ", registered:" + myRegistered);
        }
        IdeEventQueue.getInstance().flushDelayedKeyEvents();
        // Workaround for https://youtrack.jetbrains.com/issue/IDEA-214830, revert once it's fixed.
        if (SystemInfo.isJavaVersionAtLeast(8, 0, 212)) {
          // JBTerminalPanel is focused, because TerminalEventDispatcher added in focusGained and removed in focusLost
          processKeyEvent(e);
        }
        dispatchEvent(e);
        return true;
      }
      return false;
    }

    void register() {
      ApplicationManager.getApplication().assertIsDispatchThread();
      if (LOG.isDebugEnabled()) {
        LOG.debug("Register terminal event dispatcher for " +
                  JBTerminalPanel.class.getSimpleName() + "@" + System.identityHashCode(JBTerminalPanel.this));
      }
      if (myRegistered) {
        LOG.info("Already registered terminal event dispatcher");
      }
      else {
        IdeEventQueue.getInstance().addDispatcher(this, JBTerminalPanel.this);
      }
      myRegistered = true;
    }

    void unregister() {
      ApplicationManager.getApplication().assertIsDispatchThread();
      if (LOG.isDebugEnabled()) {
        LOG.debug("Unregister terminal event dispatcher for " +
                  JBTerminalPanel.class.getSimpleName() + "@" + System.identityHashCode(JBTerminalPanel.this));
      }
      if (myRegistered) {
        IdeEventQueue.getInstance().removeDispatcher(this);
      }
      myRegistered = false;
    }
  }
}
