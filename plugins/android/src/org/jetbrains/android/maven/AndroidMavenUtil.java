package org.jetbrains.android.maven;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidMavenUtil {
  private AndroidMavenUtil() {
  }

  public static boolean isMavenizedModule(@NotNull Module module) {
    AndroidMavenProvider mavenProxy = getMavenProvider();
    return mavenProxy != null && mavenProxy.isMavenizedModule(module);
  }

  @Nullable
  public static AndroidMavenProvider getMavenProvider() {
    AndroidMavenProvider[] extensions = AndroidMavenProvider.EP_NAME.getExtensions();
    return extensions.length > 0 ? extensions[0] : null;
  }
}
