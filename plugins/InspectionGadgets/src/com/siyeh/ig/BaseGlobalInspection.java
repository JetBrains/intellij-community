package com.siyeh.ig;

import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NonNls;

public abstract class BaseGlobalInspection extends GlobalInspectionTool {
  private final String shortName = null;
  private InspectionRunListener listener = null;
  private boolean telemetryEnabled = true;
  @NonNls private static final String INSPECTION = "Inspection";
  @NonNls private static final String INSPECTION_GADGETS_COMPONENT_NAME = "InspectionGadgets";

  public String getShortName() {
    if (shortName == null) {
      final Class<? extends BaseGlobalInspection> aClass = getClass();
      final String name = aClass.getName();
      return name.substring(name.lastIndexOf((int)'.') + 1,
                            name.length() - BaseGlobalInspection.INSPECTION.length());
    }
    return shortName;
  }


  private String getPropertyPrefixForInspection() {
    String shortName = getShortName();
    return getPrefix(shortName);
  }

  public String getPrefix(String shortName) {
    StringBuffer buf = new StringBuffer(shortName.length() + 10);
    buf.append(Character.toLowerCase(shortName.charAt(0)));
    for (int i = 1; i < shortName.length(); i++) {
      final char c = shortName.charAt(i);
      if (Character.isUpperCase(c)) {
        buf.append('.').append(Character.toLowerCase(c));
      }
      else {
        buf.append(c);
      }
    }
    return buf.toString();
  }

  public String getDisplayName() {
    @NonNls final String displayNameSuffix = ".display.name";
    return InspectionGadgetsBundle.message(getPropertyPrefixForInspection() + displayNameSuffix);
  }

    public boolean isGraphNeeded() {
        return true;
    }

    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WARNING;
    }

    public boolean isEnabledByDefault() {
        return false;
    }

}
