package com.intellij.htmltools.codeInspection.htmlInspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.htmltools.lang.annotation.HtmlNonExistentInternetResourcesAnnotator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * This inspections is used to enable/disable checking internet links by external annotator
 * @see HtmlNonExistentInternetResourcesAnnotator
 */
public class HtmlNonExistentInternetResourceInspection extends LocalInspectionTool {
  @NonNls public static final String SHORT_NAME = "HtmlNonExistentInternetResource";

  @NotNull
  @Override
  public String getShortName() {
    return SHORT_NAME;
  }
}
