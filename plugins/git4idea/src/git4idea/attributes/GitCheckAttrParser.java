/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.attributes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Parses the output of {@code git check-attr}.
 * All supported attributes are instances of {@link GitAttribute}. Others just ignored until needed.
 * Values are also ignored: we just need to know if there is a specified attribute on a file, or not.
 *
 * @author Kirill Likhodedov
 */
public class GitCheckAttrParser {

  private static final Logger LOG = Logger.getInstance(GitCheckAttrParser.class);
  private static final String UNSPECIFIED_VALUE = "unspecified";

  @NotNull private final Map<String, Collection<GitAttribute>> myAttributes;

  private GitCheckAttrParser(@NotNull List<String> output) {
    myAttributes = new HashMap<>();

    for (String line : output) {
      if (line.isEmpty()) {
        continue;
      }
      List<String> split = StringUtil.split(line, ":");
      LOG.assertTrue(split.size() == 3, String.format("Output doesn't match the expected format. Line: %s%nAll output:%n%s",
                                                      line, StringUtil.join(output, "\n")));
      String file = split.get(0).trim();
      String attribute = split.get(1).trim();
      String info = split.get(2).trim();

      GitAttribute attr = GitAttribute.forName(attribute);
      if (attr == null || info.equalsIgnoreCase(UNSPECIFIED_VALUE)) {
        // ignoring attributes that we are not interested in
        continue;
      }

      if (myAttributes.get(file) == null) {
        myAttributes.put(file, new ArrayList<>());
      }
      myAttributes.get(file).add(attr);
    }
  }

  @NotNull
  public static GitCheckAttrParser parse(@NotNull List<String> output) {
    return new GitCheckAttrParser(output);
  }

  @NotNull
  public Map<String, Collection<GitAttribute>> getAttributes() {
    return myAttributes;
  }

}
