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

import org.apache.commons.lang.*;

import java.nio.charset.*;
import java.util.*;

public class HgVersionCommand {

  public boolean isValid(String executable) {
    String hgExecutable = StringUtils.trim(executable);
    if (!hgExecutable.endsWith("hg")) {
      return false;
    }
    ShellCommand shellCommand = new ShellCommand();
    try {
      return !shellCommand
        .execute(Arrays.asList(hgExecutable, "version"), null, Charset.defaultCharset())
        .getOutputLines()
        .isEmpty();
    } catch (ShellCommandException e) {
      return false;
    } catch (Exception e) {
      return false;
    }
  }

}
