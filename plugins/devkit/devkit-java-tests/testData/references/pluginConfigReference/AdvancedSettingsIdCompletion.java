import com.intellij.openapi.options.advanced.AdvancedSettings;

public class AdvancedSettingsIdCompletion {
  public static void main(String[] args) {
    AdvancedSettings.getBoolean("<caret>");
  }
}