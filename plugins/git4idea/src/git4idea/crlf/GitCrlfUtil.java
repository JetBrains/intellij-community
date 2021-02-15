// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.crlf;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;

/**
 * @author Kirill Likhodedov
 */
public final class GitCrlfUtil {

  public static final @NlsSafe String ATTRIBUTE_KEY = "core.autocrlf";
  public static final @NlsSafe String RECOMMENDED_VALUE = SystemInfo.isWindows ? "true" : "input";
  public static final @NlsSafe String SUGGESTED_FIX = String.format("git config --global %s %s", ATTRIBUTE_KEY, RECOMMENDED_VALUE);
}
