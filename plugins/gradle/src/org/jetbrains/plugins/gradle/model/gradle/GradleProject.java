package org.jetbrains.plugins.gradle.model.gradle;

import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleLog;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/1/11 1:30 PM
 */
public class GradleProject extends AbstractNamedGradleEntity {

  private static final long serialVersionUID = 1L;

  private static final LanguageLevel  DEFAULT_LANGUAGE_LEVEL = LanguageLevel.JDK_1_6;
  private static final JavaSdkVersion DEFAULT_JDK_VERSION    = JavaSdkVersion.JDK_1_6;
  private static final Pattern        JDK_VERSION_PATTERN    = Pattern.compile(".*1\\.(\\d+).*");

  private final Set<GradleModule>  myModules   = new HashSet<GradleModule>();
  private final Set<GradleLibrary> myLibraries = new HashSet<GradleLibrary>();

  private JavaSdkVersion myJdkVersion    = DEFAULT_JDK_VERSION;
  private LanguageLevel  myLanguageLevel = DEFAULT_LANGUAGE_LEVEL;
  
  private Sdk mySdk;
  private String myProjectFileDirectoryPath;
  private String myCompileOutputPath;

  public GradleProject(@NotNull String projectFileDirectoryPath, @NotNull String compileOutputPath) {
    super("unnamed");
    myProjectFileDirectoryPath = GradleUtil.toCanonicalPath(projectFileDirectoryPath);
    myCompileOutputPath = GradleUtil.toCanonicalPath(compileOutputPath);
  }

  @NotNull
  public String getProjectFileDirectoryPath() {
    return myProjectFileDirectoryPath;
  }

  public void setProjectFileDirectoryPath(@NotNull String projectFileDirectoryPath) {
    myProjectFileDirectoryPath = GradleUtil.toCanonicalPath(projectFileDirectoryPath);
  }

  @NotNull
  public String getCompileOutputPath() {
    return myCompileOutputPath;
  }

  public void setCompileOutputPath(@NotNull String compileOutputPath) {
    myCompileOutputPath = GradleUtil.toCanonicalPath(compileOutputPath);
  }

  @NotNull
  public JavaSdkVersion getJdkVersion() {
    return myJdkVersion;
  }

  public void setJdkVersion(@NotNull JavaSdkVersion jdkVersion) {
    myJdkVersion = jdkVersion;
  }

  public void setJdkVersion(@Nullable String jdk) {
    if (jdk == null) {
      return;
    }
    try {
      int version = Integer.parseInt(jdk.trim());
      if (applyJdkVersion(version)) {
        return;
      } 
    }
    catch (NumberFormatException e) {
      // Ignore.
    }
    
    Matcher matcher = JDK_VERSION_PATTERN.matcher(jdk);
    if (!matcher.matches()) {
      return;
    }
    String versionAsString = matcher.group(1);
    try {
      applyJdkVersion(Integer.parseInt(versionAsString));
    }
    catch (NumberFormatException e) {
      // Ignore.
    }
  }
  
  public boolean applyJdkVersion(int version) {
    if (version < 0 || version >= JavaSdkVersion.values().length) {
      GradleLog.LOG.warn(String.format(
        "Unsupported jdk version detected (%d). Expected to get number from range [0; %d]", version, JavaSdkVersion.values().length
      ));
      return false;
    }
    for (JavaSdkVersion sdkVersion : JavaSdkVersion.values()) {
      if (sdkVersion.ordinal() == version) {
        myJdkVersion = sdkVersion;
        return true;
      }
    }
    assert false : version + ", max value: " + JavaSdkVersion.values().length;
    return false;
  }
  
  @Nullable
  public Sdk getSdk() {
    return mySdk;
  }

  public void setSdk(@NotNull Sdk sdk) {
    mySdk = sdk;
  }

  @NotNull
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  public void setLanguageLevel(@NotNull LanguageLevel level) {
    myLanguageLevel = level;
  }

  public void setLanguageLevel(@Nullable String languageLevel) {
    LanguageLevel level = LanguageLevel.parse(languageLevel);
    if (level != null) {
      myLanguageLevel = level;
    } 
  }

  public void addModule(@NotNull GradleModule module) {
    myModules.add(module);
  }
  
  @NotNull
  public Set<? extends GradleModule> getModules() {
    return myModules;
  }

  @NotNull
  public Set<? extends GradleLibrary> getLibraries() {
    return myLibraries;
  }

  public boolean addLibrary(@NotNull GradleLibrary library) {
    return myLibraries.add(library);
  }
  
  @Override
  public void invite(@NotNull GradleEntityVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public int hashCode() {
    int result = myModules.hashCode();
    result = 31 * result + myCompileOutputPath.hashCode();
    result = 31 * result + myJdkVersion.hashCode();
    result = 31 * result + myLanguageLevel.hashCode();
    result = 31 * result + super.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GradleProject that = (GradleProject)o;

    if (!super.equals(that)) return false;
    if (!myCompileOutputPath.equals(that.myCompileOutputPath)) return false;
    if (!myJdkVersion.equals(that.myJdkVersion)) return false;
    if (myLanguageLevel != that.myLanguageLevel) return false;
    if (!myModules.equals(that.myModules)) return false;

    return true;
  }

  @Override
  public String toString() {
    return String.format("project '%s':jdk='%s'|language level='%s'|modules=%s",
                         getName(), getJdkVersion(), getLanguageLevel(), getModules());
  }

  @NotNull
  @Override
  public GradleProject clone(@NotNull GradleEntityCloneContext context) {
    GradleProject result = new GradleProject(getProjectFileDirectoryPath(), getCompileOutputPath());
    result.setName(getName());
    result.setJdkVersion(getJdkVersion());
    result.setLanguageLevel(getLanguageLevel());
    for (GradleModule module : getModules()) {
      result.addModule(module.clone(context));
    }
    for (GradleLibrary library : getLibraries()) {
      result.addLibrary(library.clone(context));
    }
    return result;
  }
}
