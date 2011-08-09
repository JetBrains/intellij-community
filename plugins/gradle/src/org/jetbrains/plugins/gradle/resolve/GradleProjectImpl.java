package org.jetbrains.plugins.gradle.resolve;

import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/1/11 1:30 PM
 */
public class GradleProjectImpl implements Serializable, GradleProject {

  private static final long serialVersionUID = 1L;
  private static final LanguageLevel DEFAULT_LANGUAGE_LEVEL = LanguageLevel.JDK_1_6;
  private static final String        DEFAULT_JDK            = "1.6";

  private final Set<GradleModuleImpl> myModules = new HashSet<GradleModuleImpl>();

  private String        myJdk           = DEFAULT_JDK;
  private LanguageLevel myLanguageLevel = DEFAULT_LANGUAGE_LEVEL;
  
  @NotNull
  @Override
  public String getJdkName() {
    return myJdk;
  }

  public void setJdk(@NotNull String jdk) {
    myJdk = jdk;
  }

  @NotNull
  @Override
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  public void setLanguageLevel(@Nullable String languageLevel) {
    LanguageLevel level = LanguageLevel.parse(languageLevel);
    if (level != null) {
      myLanguageLevel = level;
    } 
  }

  public void addModule(@NotNull GradleModuleImpl module) {
    myModules.add(module);
  }
  
  @NotNull
  @Override
  public Set<? extends GradleModule> getModules() {
    return myModules;
  }
}
