package com.intellij.codeInspection;

import com.intellij.codeInspection.duplicatePropertyInspection.DuplicatePropertyInspection;
import com.intellij.codeInspection.duplicateStringLiteral.DuplicateStringLiteralInspection;
import com.intellij.codeInspection.i18n.I18nInspection;
import com.intellij.codeInspection.i18n.InconsistentResourceBundleInspection;
import com.intellij.codeInspection.i18n.InvalidPropertyKeyInspection;
import com.intellij.lang.properties.UnusedMessageFormatParameterInspection;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: 11.02.2009
 * Time: 17:54:45
 * To change this template use File | Settings | File Templates.
 */
public class JavaInspectionToolProvider implements InspectionToolProvider {
  public Class[] getInspectionClasses() {
    return new Class[] {
      I18nInspection.class,
      InvalidPropertyKeyInspection.class,
      InconsistentResourceBundleInspection.class,
      UnusedMessageFormatParameterInspection.class,
      DuplicateStringLiteralInspection.class,
      DuplicatePropertyInspection.class,

    };
  }
}
