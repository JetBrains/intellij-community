// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl;

import com.intellij.execution.process.OSProcessUtil;
import com.intellij.ide.ApplicationInitializedListenerJavaShim;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.Strings;
import com.sun.tools.attach.VirtualMachine;
import org.jetbrains.annotations.NonNls;
import sun.tools.attach.HotSpotVirtualMachine;

import java.io.File;

// NOTE: compile with --add-exports jdk.attach/sun.tools.attach=ALL-UNNAMED in module-aware jdk
final class JitSuppressor extends ApplicationInitializedListenerJavaShim {
  private static final Logger LOG = Logger.getInstance(JitSuppressor.class);
  private static final String SELF_ATTACH_PROP = "jdk.attach.allowAttachSelf";
  private static final String EXCLUDE_ALL_FROM_C2_CLAUSE = "{ match : [\"*.*\"], c2 : { Exclude : true }}";

  // Limit C2 compilation to the given packages only. The package set was computed by test performance analysis:
  // it's supposed to make performance tests (not all yet) timing comparable with full C2, and at the same time,
  //  the IDE does not load CPU heavily with this limitation.
  private static final @NonNls String[] C2_WHITELIST = {
    "com/intellij/openapi/application/*.*",
    "com/intellij/openapi/editor/*.*",
    "com/intellij/openapi/project/*.*",
    "com/intellij/openapi/util/text/*.*",
    "com/intellij/openapi/util/io/*.*", // FileUtil
    "com/intellij/openapi/vfs/*.*",
    "com/intellij/openapi/fileTypes/*.*",
    "com/intellij/openapi/progress/*.*",
    "com/intellij/psi/codeStyle/*.*",
    "com/intellij/psi/util/*.*",
    "com/intellij/psi/impl/source/tree/*.*",
    "com/intellij/util/*.*",
    "com/intellij/semantic/*.*",
    "com/intellij/lang/impl/*.*", // PsiBuilder and related classes
    "com/intellij/lang/parser/*.*", // GeneratedParserUtilBase
    "java/lang/*.*",
    "java/math/*.*",
    "jdk/internal/*.*",
    "sun/*.*",
  };

  // masks matching methods with longest compilation durations which we have no control over
  private static final @NonNls String[] C2_BLACKLIST = {
    "javax/swing/*.*",
    "javax/awt/*.*",
    "sun/awt/*.*",
    "sun/java2d/*.*",
  };
  private static final boolean ourBlacklistMode = true;

  JitSuppressor() {
    if (!Boolean.getBoolean("enable.jit.suppressor")) {
      throw ExtensionNotApplicableException.create();
    }
    // java.specification.version has values "1.8", "1.7" e.t.c. for jdk <= 8 and "9", "10", "11", 12" for others
    if (System.getProperty("java.specification.version").contains(".")) {
      throw ExtensionNotApplicableException.create();
    }
  }

  @Override
  public void componentsInitialized() {
    String javaSpecVendor = System.getProperty("java.vm.specification.vendor");
    if (!"Oracle Corporation".equals(javaSpecVendor)) {
      LOG.warn("JitSuppressor functionality is not supported on non-Oracle vm. This one is " + javaSpecVendor);
      return;
    }

    if (!"true".equals(System.getProperty(SELF_ATTACH_PROP))) {
      String msg = "JitSuppressor wasn't registered. Please ensure the command line contains -D" + SELF_ATTACH_PROP + "=true";
      if (ApplicationManagerEx.isInStressTest()) {
        LOG.warn(msg + " to get production-like performance");
      }
      else if (!ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.warn(msg);
      }
      return;
    }

    LOG.info("JitSuppressor is active");

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(PowerSaveMode.TOPIC, new PowerSaveMode.Listener() {
      @Override
      public void powerSaveStateChanged() {
        JitSuppressor.powerSaveStateChanged();
      }
    });

    // call the handler to make initial setup
    powerSaveStateChanged();
  }

  private static void powerSaveStateChanged() {
    String directives = "[" + generateDirectives() + "]";

    Runnable runnable = () -> setDirectives(directives);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().executeOnPooledThread(runnable);
    }
  }

  private static void setDirectives(String directives) {
    File tmpFile = null;
    try {
      tmpFile = FileUtil.createTempFile("c2_directive", "json");
      FileUtil.writeToFile(tmpFile, directives);

      VirtualMachine vm = VirtualMachine.attach(OSProcessUtil.getApplicationPid());
      HotSpotVirtualMachine hvm = (HotSpotVirtualMachine)vm;
      hvm.executeJCmd("Compiler.directives_clear");
      hvm.executeJCmd("Compiler.directives_add " + tmpFile.getAbsolutePath());
      vm.detach();
    }
    catch (Exception e) {
      LOG.error(e);
    }
    finally {
      if (tmpFile != null) {
        FileUtil.delete(tmpFile);
      }
    }
  }

  private static String generateDirectives() {
    if (PowerSaveMode.isEnabled()) {
      return EXCLUDE_ALL_FROM_C2_CLAUSE;
    }

    if (ourBlacklistMode) {
      return Strings.join(C2_BLACKLIST, mask -> "{ match: [\"" + mask + "\"], c2: { Exclude: true, }, },", "");
    }
    return Strings.join(C2_WHITELIST, mask -> "{ match: [\"" + mask + "\"], c2: { Exclude: false, }, },", "") +
           EXCLUDE_ALL_FROM_C2_CLAUSE;
  }
}
