/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.validators;

import com.intellij.util.Function;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * The test for {@link GitRefNameValidator}.
 * Tests are based on the <a href="http://www.kernel.org/pub/software/scm/git/docs/git-check-ref-format.html">
 * specification of valid Git references</a>
 *
 * @author Kirill Likhodedov
 */
public class GitRefNameValidatorTest {
  
  @DataProvider(name = "valid")
  public Object[][] createValidData() {
    return new Object[][] {
      { "WORD",              "branch"                 },
      { "UNDERSCORED_WORD",  "new_branch"             },
      { "HIERARCHY",         "user/branch"            },
      { "HIERARCHY_2",       "user/branch/sub_branch" },
      { "BEGINS_WITH_SLASH", "/branch"                }, // actual branch name will be with trimmed slash
      { "NON_CONS_DOTS",     "complex.branch.name"    }
    };
  }
  
  @DataProvider(name = "simple_invalid")
  public Object[][] createInvalidData() {
    return new Object[][] {
      { "BEGIN_WITH_DOT",      ".branch"      },
      { "ONLY_DOT",            "."            },
      { "ENDS_WITH_SLASH",     "branch/"      },
      { "ENDS_WITH_DOT",       "branch."      },
      { "ENDS_WITH_LOCK",      "branch.lock"  },
      { "TWO_DOTS_1",          "branch..name" },
      { "TWO_DOTS_2",          "..name"       }, 
      { "TWO_DOTS_3",          "..branch"     }
    };
  }

  private static final String[] ILLEGAL_CHARS = { " ", "~", "^", ":", "?", "*", "[", "@{", "\\" };

  @DataProvider(name = "invalid_chars")
  public Object[][] createInvalidCharsData() {
    return populateWithIllegalChars(ILLEGAL_CHARS, new Function<String, String>() {
      @Override public String fun(String s) {
        return s;
      }
    });
  }

  private static final int CONTROL_CHARS_START = 5; // we can't test from 0 to 4 via @DataProvider due to TestNG limitations
  private static final int CONTROL_CHARS_END = 31;
  private static final int CONTROL_CHARS_SIZE = CONTROL_CHARS_END - CONTROL_CHARS_START + 1;
  private static final String[] CONTROL_CHARS = new String[CONTROL_CHARS_SIZE + 1]; // + DEL
  static {
    for (int i = CONTROL_CHARS_START; i <= CONTROL_CHARS_END; i++) {
      CONTROL_CHARS[i-CONTROL_CHARS_START] = String.valueOf((char)i);
    }
    CONTROL_CHARS[CONTROL_CHARS_SIZE] = "\u007F"; // DEL
  }

  @DataProvider(name = "invalid_control_chars")
  public Object[][] createInvalidControlCharsData() {
    return populateWithIllegalChars(CONTROL_CHARS, new Function<String, String>() {
      @Override public String fun(String s) {
        Character c = s.charAt(0);
        return "\\u00" + Integer.toHexString(c);
      }
    });
  }

  private static Object[][] populateWithIllegalChars(String[] illegalChars, Function<String, String> toString) {
    Object[][] data = new Object[illegalChars.length][];
    for (int i = 0; i < illegalChars.length; i++) {
      data[i] = new Object[]{
        toString.fun(illegalChars[i]), "bra" + illegalChars[i] + "nch"
      };
    }
    return data;
  }

  @Test(dataProvider = "valid")
  public void testValid(String testName, String branchName) {
    assertValid(branchName);
  }

  @Test(dataProvider = "simple_invalid")
  public void testSimpleInvalid(String testName, String branchName) {
    assertInvalid(branchName);
  }

  @Test(dataProvider = "invalid_chars")
  public void testInvalidChars(String testName, String branchName) {
    assertInvalid(branchName);
  }

  @Test(dataProvider = "invalid_control_chars")
  public void control_chars_are_invalid(String testName, String branchName) {
    assertInvalid(branchName);
  }
  
  // \u0000 to \u0004 can't be passed to the TestNG DataProvider - see org.testng.remote.strprotocol.MessageHelper
  @Test
  public void control_chars_from_0_to_4_are_invalid() {
    for (int i = 0; i < 5; i++) {
      assertInvalid("bra" + (char)i + "nch");
    }
  }

  private static void assertValid(String branchName) {
    assertTrue(GitRefNameValidator.getInstance().checkInput(branchName), "Should be valid");
    assertTrue(GitRefNameValidator.getInstance().canClose(branchName), "Should be valid");
  }

  private static void assertInvalid(String branchName) {
    assertFalse(GitRefNameValidator.getInstance().checkInput(branchName), "Should be invalid");
    assertFalse(GitRefNameValidator.getInstance().canClose(branchName), "Should be invalid");
  }

}