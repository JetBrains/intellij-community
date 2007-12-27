/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
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