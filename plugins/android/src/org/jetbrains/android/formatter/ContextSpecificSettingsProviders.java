package org.jetbrains.android.formatter;

/**
 * @author Eugene.Kudelevsky
 */
public class ContextSpecificSettingsProviders {
  public static final Provider<AndroidXmlCodeStyleSettings.LayoutSettings> LAYOUT =
    new Provider<AndroidXmlCodeStyleSettings.LayoutSettings>() {
      @Override
      public AndroidXmlCodeStyleSettings.LayoutSettings getSettings(AndroidXmlCodeStyleSettings baseSettings) {
        return baseSettings.LAYOUT_SETTINGS;
      }
    };

  public static final Provider<AndroidXmlCodeStyleSettings.ManifestSettings> MANIFEST =
    new Provider<AndroidXmlCodeStyleSettings.ManifestSettings>() {
      @Override
      public AndroidXmlCodeStyleSettings.ManifestSettings getSettings(AndroidXmlCodeStyleSettings baseSettings) {
        return baseSettings.MANIFEST_SETTINGS;
      }
    };

  public static final Provider<AndroidXmlCodeStyleSettings.ValueResourceFileSettings> VALUE_RESOURCE_FILE =
    new Provider<AndroidXmlCodeStyleSettings.ValueResourceFileSettings>() {
      @Override
      public AndroidXmlCodeStyleSettings.ValueResourceFileSettings getSettings(AndroidXmlCodeStyleSettings baseSettings) {
        return baseSettings.VALUE_RESOURCE_FILE_SETTINGS;
      }
    };

  public static final Provider<AndroidXmlCodeStyleSettings.OtherSettings> OTHER =
    new Provider<AndroidXmlCodeStyleSettings.OtherSettings>() {
      @Override
      public AndroidXmlCodeStyleSettings.OtherSettings getSettings(AndroidXmlCodeStyleSettings baseSettings) {
        return baseSettings.OTHER_SETTINGS;
      }
    };

  abstract static class Provider<T extends AndroidXmlCodeStyleSettings.MySettings> {
    abstract T getSettings(AndroidXmlCodeStyleSettings baseSettings);
  }
}
