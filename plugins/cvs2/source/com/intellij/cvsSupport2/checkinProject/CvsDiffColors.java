package com.intellij.cvsSupport2.checkinProject;

import com.intellij.openapi.editor.colors.TextAttributesKey;

public interface CvsDiffColors {
  TextAttributesKey DIFF_UNKNOWN = TextAttributesKey.createTextAttributesKey("DIFF_UNKNOWN");
  TextAttributesKey DIFF_IGNORED = TextAttributesKey.createTextAttributesKey("DIFF_IGNORED");
  TextAttributesKey DIFF_DELETED_FROM_FS = TextAttributesKey.createTextAttributesKey("DIIF_DELETED_FROM_FS");
}
