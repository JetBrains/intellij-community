import java.util.concurrent.TimeUnit;
import com.intellij.openapi.options.advanced.AdvancedSettings;

public class AdvancedSettingsId {
  public static void main(String[] args) {
    AdvancedSettings.getBoolean("advancedSettingId");
    AdvancedSettings.getInt("advancedSettingId");
    AdvancedSettings.getString("advancedSettingId");
    AdvancedSettings.getEnum("advancedSettingId", TimeUnit.class);
    AdvancedSettings.getBoolean("<error descr="Cannot resolve advanced setting ID 'INVALID_VALUE'">INVALID_VALUE</error>");
    AdvancedSettings.getInt("<error descr="Cannot resolve advanced setting ID 'INVALID_VALUE'">INVALID_VALUE</error>");
    AdvancedSettings.getString("<error descr="Cannot resolve advanced setting ID 'INVALID_VALUE'">INVALID_VALUE</error>");
    AdvancedSettings.getEnum("<error descr="Cannot resolve advanced setting ID 'INVALID_VALUE'">INVALID_VALUE</error>", TimeUnit.class);

    AdvancedSettings.getDefaultBoolean("advancedSettingId");
    AdvancedSettings.getDefaultInt("advancedSettingId");
    AdvancedSettings.getDefaultString("advancedSettingId");
    AdvancedSettings.getDefaultEnum("advancedSettingId", TimeUnit.class);
    AdvancedSettings.getDefaultBoolean("<error descr="Cannot resolve advanced setting ID 'INVALID_VALUE'">INVALID_VALUE</error>");
    AdvancedSettings.getDefaultInt("<error descr="Cannot resolve advanced setting ID 'INVALID_VALUE'">INVALID_VALUE</error>");
    AdvancedSettings.getDefaultString("<error descr="Cannot resolve advanced setting ID 'INVALID_VALUE'">INVALID_VALUE</error>");
    AdvancedSettings.getDefaultEnum("<error descr="Cannot resolve advanced setting ID 'INVALID_VALUE'">INVALID_VALUE</error>", TimeUnit.class);

    AdvancedSettings.setBoolean("advancedSettingId", false);
    AdvancedSettings.setInt("advancedSettingId", 42);
    AdvancedSettings.setString("advancedSettingId", "dummy");
    AdvancedSettings.setEnum("advancedSettingId", TimeUnit.SECONDS);
    AdvancedSettings.setBoolean("<error descr="Cannot resolve advanced setting ID 'INVALID_VALUE'">INVALID_VALUE</error>", false);
    AdvancedSettings.setInt("<error descr="Cannot resolve advanced setting ID 'INVALID_VALUE'">INVALID_VALUE</error>", 42);
    AdvancedSettings.setString("<error descr="Cannot resolve advanced setting ID 'INVALID_VALUE'">INVALID_VALUE</error>", "dummy");
    AdvancedSettings.setEnum("<error descr="Cannot resolve advanced setting ID 'INVALID_VALUE'">INVALID_VALUE</error>", TimeUnit.SECONDS);
  }
}