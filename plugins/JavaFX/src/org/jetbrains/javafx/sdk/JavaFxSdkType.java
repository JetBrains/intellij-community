package org.jetbrains.javafx.sdk;

import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.javafx.JavaFxBundle;
import org.jetbrains.javafx.JavaFxFileType;

import javax.swing.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxSdkType extends SdkType {
  private final Map<String, String> myCachedVersionStrings = new HashMap<String, String>();
  private final Pattern myVersionStringPattern = Pattern.compile("^javafx ([\\.\\d]*).*$");

  public JavaFxSdkType() {
    super(JavaFxBundle.message("javafx.sdk.name"));
  }

  @Override
  public String suggestHomePath() {
    if (SystemInfo.isWindows) {
      return "C:\\Program Files\\JavaFX\\";
    }
    else if (SystemInfo.isMac) {
      return "/Library/Frameworks/JavaFX.framework/Versions/";
    }
    return null;
  }

  @Override
  public boolean isValidSdkHome(String path) {
    return JavaFxSdkUtil.isSdkHome(path);
  }

  @Override
  public String suggestSdkName(String currentSdkName, String sdkHome) {
    final String versionString = getVersionString(sdkHome);
    String result = JavaFxBundle.message("javafx.sdk.name");
    if (versionString != null) {
      final Matcher matcher = myVersionStringPattern.matcher(versionString);
      if (matcher.matches()) {
        result += "-" + matcher.group(1);
      }
    }
    return result;
  }

  @Override
  public AdditionalDataConfigurable createAdditionalDataConfigurable(SdkModel sdkModel, SdkModificator sdkModificator) {
    return null;
  }

  @Override
  public void saveAdditionalData(SdkAdditionalData additionalData, Element additional) {
  }

  @Override
  public String getVersionString(String sdkHome) {
    final String cachedString = myCachedVersionStrings.get(sdkHome);
    if (cachedString == null) {
      final String versionString = SdkVersionUtil.readVersionFromProcessOutput(sdkHome, new String[]{sdkHome + "/bin/javafx", "-version"},
                                                                               "javafx");
      if (versionString == null || versionString.length() == 0) {
        return null;
      }
      myCachedVersionStrings.put(sdkHome, versionString);
      return versionString;
    }
    return cachedString;
  }

  @Override
  public boolean isRootTypeApplicable(OrderRootType type) {
    return type == OrderRootType.CLASSES || type == OrderRootType.SOURCES || type == JavadocOrderRootType.getInstance();
  }

  @Override
  public void setupSdkPaths(final Sdk sdk) {
    final VirtualFile[] classes = JavaFxSdkUtil.findClasses(sdk);
    final VirtualFile sources = JavaFxSdkUtil.findSources(sdk);
    final VirtualFile docs = JavaFxSdkUtil.findDocs(sdk, "docs/api");

    final SdkModificator sdkModificator = sdk.getSdkModificator();
    final Set<VirtualFile> previousRoots = new LinkedHashSet<VirtualFile>(Arrays.asList(sdkModificator.getRoots(OrderRootType.CLASSES)));
    sdkModificator.removeRoots(OrderRootType.CLASSES);
    ContainerUtil.addAll(previousRoots, classes);

    Arrays.sort(classes, new Comparator<VirtualFile>() {
      public int compare(VirtualFile o1, VirtualFile o2) {
        return o1.getPath().compareTo(o2.getPath());
      }
    });
    for (VirtualFile aClass : classes) {
      sdkModificator.addRoot(aClass, OrderRootType.CLASSES);
    }
    if (sources != null) {
      sdkModificator.addRoot(sources, OrderRootType.SOURCES);
    }
    if (docs != null) {
      sdkModificator.addRoot(docs, JavadocOrderRootType.getInstance());
    }

    sdkModificator.commitChanges();
  }

  @Override
  public Icon getIconForAddAction() {
    return getIcon();
  }

  @Override
  public Icon getIcon() {
    return JavaFxFileType.INSTANCE.getIcon();
  }

  @Override
  public String getPresentableName() {
    return getName();
  }
}
