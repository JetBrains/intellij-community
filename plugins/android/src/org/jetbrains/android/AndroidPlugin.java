package org.jetbrains.android;

import com.android.ddmlib.AndroidDebugBridge;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ultimate.PluginVerifier;
import com.intellij.ultimate.UltimateVerifier;
import org.jetbrains.android.ddms.AdbManager;
import org.jetbrains.android.ddms.AdbNotRespondingException;
import org.jetbrains.annotations.NotNull;

/**
 * @author coyote
 */
public class AndroidPlugin implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.AndroidPlugin");

  @NotNull
  public String getComponentName() {
    return "AndroidApplicationComponent";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    try {
      AdbManager.run(new Runnable() {
        public void run() {
          AndroidDebugBridge.terminate();
        }
      }, false);
    }
    catch (AdbNotRespondingException e) {
      LOG.info(e);
    }
  }
}
