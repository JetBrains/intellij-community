package org.jetbrains.android.sdk;

/**
 * @author Eugene.Kudelevsky
 */
public interface AndroidPlatformChooserListener {
  void platformChanged(AndroidPlatform oldPlatform);
}
