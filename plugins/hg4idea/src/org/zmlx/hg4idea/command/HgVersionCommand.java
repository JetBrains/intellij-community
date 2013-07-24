// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.command;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.ShellCommand;
import org.zmlx.hg4idea.execution.ShellCommandException;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HgVersionCommand {

  private static final Logger LOGGER = Logger.getInstance(HgVersionCommand.class);
  private static final Pattern HG_VERSION_PATTERN = Pattern.compile(".+\\(\\s*version\\s+([0-9]+\\.[0-9]*)\\+?([0-9]*)[0-9\\.]*\\s*\\)\\s*");

  public Double getVersion(String executable, boolean isRunViaBash) {
    String hgExecutable = executable == null ? null : executable.trim();
    ShellCommand shellCommand = new ShellCommand(isRunViaBash);
    List<String> cmdArgs = new ArrayList<String>();
    cmdArgs.add(hgExecutable);
    cmdArgs.add("version");
    cmdArgs.add("-q");
    try {
      HgCommandResult versionResult = shellCommand
        .execute(cmdArgs, null, Charset.defaultCharset());
      return parseVersion(versionResult);
    }
    catch (InterruptedException e) {
      LOGGER.error(e);
    }
    catch (ShellCommandException e) {
      LOGGER.error(e);
    }
    return null;
  }

  @Nullable
  private static Double parseVersion(HgCommandResult versionResult) {
    Matcher matcher = HG_VERSION_PATTERN.matcher(versionResult.getRawOutput());
    if (matcher.matches()) {
      return Double.valueOf(matcher.group(1).concat(matcher.group(2)));
    }
    return null;
  }
}
