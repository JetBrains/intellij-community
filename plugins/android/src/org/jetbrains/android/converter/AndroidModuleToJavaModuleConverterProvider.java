package org.jetbrains.android.converter;

import com.intellij.conversion.*;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidModuleToJavaModuleConverterProvider extends ConverterProvider {
  public AndroidModuleToJavaModuleConverterProvider() {
    super("android-module-to-java-module");
  }

  @NotNull
  @Override
  public String getConversionDescription() {
    return "Android modules will be converted to Java modules with Android facet";
  }

  @NotNull
  @Override
  public ProjectConverter createConverter(@NotNull ConversionContext context) {
    return new ProjectConverter() {
      @Override
      public ConversionProcessor<ModuleSettings> createModuleFileConverter() {
        return new ConversionProcessor<ModuleSettings>() {
          @Override
          public boolean isConversionNeeded(ModuleSettings moduleSettings) {
            return "ANDROID_MODULE".equals(moduleSettings.getModuleType());
          }

          @Override
          public void process(ModuleSettings moduleSettings) throws CannotConvertException {
            moduleSettings.setModuleType("JAVA_MODULE");
          }
        };
      }
    };
  }
}
