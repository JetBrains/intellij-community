// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.attributes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the output of {@code git check-attr}.
 * All supported attributes are instances of {@link GitAttribute}. Others just ignored until needed.
 * Values are also ignored: we just need to know if there is a specified attribute on a file, or not.
 *
 * @author Kirill Likhodedov
 */
public final class GitCheckAttrParser {

  private static final Logger LOG = Logger.getInstance(GitCheckAttrParser.class);
  private static final String UNSPECIFIED_VALUE = "unspecified";
  private static final Pattern PATTERN = Pattern.compile("(.*):([^:]+):([^:]+)");

  private final @NotNull Map<String, Collection<GitAttribute>> myAttributes;

  private GitCheckAttrParser(@NotNull List<String> output) {
    myAttributes = new HashMap<>();

    for (String line : output) {
      if (line.isEmpty()) {
        continue;
      }

      Matcher matcher = PATTERN.matcher(line);
      if (!matcher.matches()) {
        LOG.error(String.format("Output doesn't match the expected format. Line: %s%nAll output:%n%s",
                                line, StringUtil.join(output, "\n")));
      }

      String file = matcher.group(1).trim();
      String attribute = matcher.group(2).trim();
      String info = matcher.group(3).trim();

      GitAttribute attr = GitAttribute.forName(attribute);
      if (attr == null || info.equalsIgnoreCase(UNSPECIFIED_VALUE)) {
        // ignoring attributes that we are not interested in
        continue;
      }

      myAttributes.computeIfAbsent(file, f -> new ArrayList<>()).add(attr);
    }
  }

  public static @NotNull GitCheckAttrParser parse(@NotNull List<String> output) {
    return new GitCheckAttrParser(output);
  }

  public @NotNull Map<String, Collection<GitAttribute>> getAttributes() {
    return myAttributes;
  }
}
