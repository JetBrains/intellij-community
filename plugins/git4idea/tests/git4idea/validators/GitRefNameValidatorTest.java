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
import com.intellij.util.containers.ContainerUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The test for {@link GitRefNameValidator}.
 * Tests are based on the <a href="http://www.kernel.org/pub/software/scm/git/docs/git-check-ref-format.html">
 * specification of valid Git references</a>
 */
@RunWith(Parameterized.class)
public class GitRefNameValidatorTest {

  private static final String[] ILLEGAL_CHARS = { " ", "~", "^", ":", "?", "*", "[", "@{", "\\" };
  private static final int CONTROL_CHARS_START = 0;
  private static final int CONTROL_CHARS_END = 31;
  private static final int CONTROL_CHARS_SIZE = CONTROL_CHARS_END - CONTROL_CHARS_START + 1;
  private static final String[] CONTROL_CHARS = new String[CONTROL_CHARS_SIZE + 1]; // + DEL

  static {
    for (int i = CONTROL_CHARS_START; i <= CONTROL_CHARS_END; i++) {
      CONTROL_CHARS[i-CONTROL_CHARS_START] = String.valueOf((char)i);
    }
    CONTROL_CHARS[CONTROL_CHARS_SIZE] = "\u007F"; // DEL
  }

  private final String myRefNameToTest;
  private final boolean myIsExpectedValid;

  private static Object[][] createValidData() {
      return new Object[][] {
        { "WORD",              "branch"                 },
        { "UNDERSCORED_WORD",  "new_branch"             },
        { "HIERARCHY",         "user/branch"            },
        { "HIERARCHY_2",       "user/branch/sub_branch" },
        { "BEGINS_WITH_SLASH", "/branch"                }, // actual branch name will be with trimmed slash
        { "NON_CONS_DOTS",     "complex.branch.name"    },
        { "GERRIT_PATTERN",    "refs/for/master%topic=my-cool-feature,r=some-reviewer"},
        { "CONTAINS_MINUS",    "b-ranch"},
        { "STARTS_WITH_PLUS",  "+branch"}
      };
    }

  private static Object[][] createInvalidData() {
      return new Object[][] {
        { "BEGIN_WITH_DOT",      ".branch"      },
        { "ONLY_DOT",            "."            },
        { "ENDS_WITH_SLASH",     "branch/"      },
        { "ENDS_WITH_DOT",       "branch."      },
        { "ENDS_WITH_LOCK",      "branch.lock"  },
        { "TWO_DOTS_1",          "branch..name" },
        { "TWO_DOTS_2",          "..name"       },
        { "TWO_DOTS_3",          "..branch"     },
        { "EMPTY",               ""},
        { "SPACES",              "  "},
        { "STARTS_WITH_MINUS",    "-branch" }
      };
    }

  public static Object[][] createInvalidCharsData() {
    return populateWithIllegalChars(ILLEGAL_CHARS, s -> s);
  }

  public static Object[][] createInvalidControlCharsData() {
    return populateWithIllegalChars(CONTROL_CHARS, s -> {
      Character c = s.charAt(0);
      return "\\u00" + Integer.toHexString(c);
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

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    Collection<Object[]> data = ContainerUtil.newArrayList();
    populateData(data, createValidData(), true);
    populateData(data, createInvalidData(), false);
    populateData(data, createInvalidCharsData(), false);
    populateData(data, createInvalidControlCharsData(), false);
    return data;
  }

  private static void populateData(Collection<Object[]> data, Object[][] source, boolean valid) {
    for (Object[] testCase : source) {
      data.add(new Object[] {testCase[0], testCase[1], valid});
    }
  }

  @SuppressWarnings("UnusedParameters")
  public GitRefNameValidatorTest(String name, String refNameToTest, boolean valid) {
    myRefNameToTest = refNameToTest;
    myIsExpectedValid = valid;
  }

  @Test
  public void testAll() {
    if (myIsExpectedValid) {
      assertValid(myRefNameToTest);
    }
    else {
      assertInvalid(myRefNameToTest);
    }
  }
  
  private static void assertValid(String branchName) {
    assertTrue("Should be valid", GitRefNameValidator.getInstance().checkInput(branchName));
    assertTrue("Should be valid", GitRefNameValidator.getInstance().canClose(branchName));
  }

  private static void assertInvalid(String branchName) {
    assertFalse("Should be invalid", GitRefNameValidator.getInstance().checkInput(branchName));
    assertFalse("Should be invalid", GitRefNameValidator.getInstance().canClose(branchName));
  }

}