package org.jetbrains.plugins.groovy.inspections.secondUnsafeCall;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import junit.framework.Test;
import org.jetbrains.plugins.groovy.annotator.inspections.SecondUnsafeCallQuickFix;
import org.jetbrains.plugins.groovy.codeInspection.secondUnsafeCall.SecondUnsafeCallInspection;
import org.jetbrains.plugins.groovy.inspections.InspectionTestCase;

/**
 * User: Dmitry.Krasilschikov
 * Date: 15.11.2007
 */
public class SecondUnsafeCallTest extends InspectionTestCase {
  protected static final String DATA_PATH = "test/org/jetbrains/plugins/groovy/inspections/secondUnsafeCall/data";

  public SecondUnsafeCallTest () {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }

  public LocalQuickFix getLocalQuickFix() {
    return new SecondUnsafeCallQuickFix();
  }

  public LocalInspectionTool getLocalInspectionTool() {
    return new SecondUnsafeCallInspection();
  }

  public static Test suite(){
    return new SecondUnsafeCallTest();
  }
}