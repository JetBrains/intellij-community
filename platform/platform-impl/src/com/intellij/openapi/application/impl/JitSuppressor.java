// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.execution.process.OSProcessUtil;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.sun.tools.attach.VirtualMachine;
import sun.tools.attach.HotSpotVirtualMachine;

import java.io.File;

// NOTE: compile with --add-exports jdk.attach/sun.tools.attach=ALL-UNNAMED in module-aware jdk
public class JitSuppressor implements PowerSaveMode.Listener {
  private static final Logger LOG = Logger.getInstance(JitSuppressor.class);
  private static final String SELF_ATTACH_PROP = "jdk.attach.allowAttachSelf";
  private static final String EXCLUDE_ALL_FROM_C2_CLAUSE = "{ match : [\"*.*\"], c2 : { Exclude : true }}";
  // Limit C2 compilation with the given packages only. The package set was computed by test performance analysis:
  // it makes performance tests timing comparable with full C2 and same time IDE does not load CPU heavily with this limitation.
  private static final String[] C2_LIMIT_MASKS = {
    "com/intellij/openapi/application/*.*",
    "com/intellij/openapi/editor/*.*",
    "com/intellij/openapi/project/*.*",
    "com/intellij/openapi/util/text/*.*",
    "com/intellij/openapi/vfs/*.*",
    "com/intellij/openapi/fileTypes/*.*",
    "com/intellij/psi/codeStyle/*.*",
    "com/intellij/psi/util/*.*",
    "com/intellij/util/*.*",
    "java/lang/*.*",
    "java/math/*.*",
    "sun/*.*",
  };

  public JitSuppressor() {
    if (!SystemProperties.getBooleanProperty("enable.jit.suppressor", false)) {
      return;
    }

    // java.specification.version has values "1.8", "1.7" e.t.c. for jdk <= 8 and "9", "10", "11", 12" for others
    if (System.getProperty("java.specification.version").contains(".")) return;

    String javaSpecVendor = System.getProperty("java.vm.specification.vendor");
    if (!"Oracle Corporation".equals(javaSpecVendor)) {
      LOG.warn("JitSuppressor functionality is not supported on non-Oracle vm. This one is " + javaSpecVendor);
      return;
    }

    if (!"true".equals(System.getProperty(SELF_ATTACH_PROP))) {
      String msg = "JitSuppressor wasn't registered. Please ensure the command line contains -D" + SELF_ATTACH_PROP + "=true";
      if (ApplicationInfoImpl.isInStressTest()) {
        LOG.warn(msg + " to get production-like performance");
      }
      else if (!ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.warn(msg);
      }
      return;
    }
    
    LOG.info("JitSuppressor is active");

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(PowerSaveMode.TOPIC, this);

    // call the handler to make initial setup
    powerSaveStateChanged();
  }

  @Override
  public void powerSaveStateChanged() {
    String directives = generateDirectives();

    Runnable runnable = () -> setDirectives(directives);
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      runnable.run();
    } else {
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
    String includes = PowerSaveMode.isEnabled()
                      ? ""
                      : StringUtil.join(C2_LIMIT_MASKS, mask -> "{ match: [\"" + mask + "\"], c2: { Exclude: false, }, },", "");
    return "[" + includes + EXCLUDE_ALL_FROM_C2_CLAUSE + "]";
  }
}
