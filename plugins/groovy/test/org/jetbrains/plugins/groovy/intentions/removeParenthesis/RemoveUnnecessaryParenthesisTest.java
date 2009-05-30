/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
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
 */

package org.jetbrains.plugins.groovy.intentions.removeParenthesis;

import com.intellij.idea.Bombed;
import junit.framework.Test;
import org.jetbrains.plugins.groovy.intentions.conversions.RemoveParenthesesFromMethodCallIntention;
import org.jetbrains.plugins.groovy.testcases.simple.SimpleGroovyFileSetTestCase;
import org.jetbrains.plugins.groovy.util.PathUtil;

import java.util.Calendar;

/**
 * User: Dmitry.Krasilschikov
 * Date: 30.01.2009
 */
@Bombed(month = Calendar.JUNE, day = 30)
public class RemoveUnnecessaryParenthesisTest extends SimpleGroovyFileSetTestCase {
  protected static final String DATA_PATH = PathUtil.getDataPath(RemoveUnnecessaryParenthesisTest.class);

  public RemoveUnnecessaryParenthesisTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }

  public String transform(String testName, String[] data) throws Exception {
    RemoveParenthesesFromMethodCallIntention intention = new RemoveParenthesesFromMethodCallIntention();

    //intention.invoke(myProject, );


    return null;
  }

  public static Test suite(){
    return new RemoveUnnecessaryParenthesisTest();
  }
}
