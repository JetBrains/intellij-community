package org.jetbrains.android.dom.manifest;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public interface CompatibleScreens extends ManifestElement {
  List<CompatibleScreensScreen> getScreens();
}
