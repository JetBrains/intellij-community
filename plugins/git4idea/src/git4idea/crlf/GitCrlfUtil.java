// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.crlf;

import com.intellij.openapi.util.SystemInfo;

/**
 * @author Kirill Likhodedov
 */
public class GitCrlfUtil {

  public static final String RECOMMENDED_VALUE = SystemInfo.isWindows ? "true" : "input";

}
