package org.jetbrains.android.inspections;

import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import org.jetbrains.android.dom.AndroidDomElement;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class AndroidDomInspection extends BasicDomElementsInspection<AndroidDomElement> {
  public AndroidDomInspection() {
    super(AndroidDomElement.class);
  }

  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return AndroidBundle.message("android.inspections.group.name");
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return AndroidBundle.message("android.inspections.dom.name");
  }

  @NonNls
  @NotNull
  public String getShortName() {
    return "AndroidDomInspection";
  }
}