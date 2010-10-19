package org.jetbrains.android.run;

import com.android.ddmlib.IDevice;

/**
 * @author coyote
 */
public interface DebugLauncher {
    void launchDebug(IDevice device, String debugPort);
}
