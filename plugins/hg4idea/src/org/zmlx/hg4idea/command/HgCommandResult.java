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

import com.intellij.openapi.util.text.LineTokenizer;

import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

public final class HgCommandResult {

  static final HgCommandResult EMPTY = new HgCommandResult(new StringWriter(), new StringWriter(), 0);

  private final StringWriter out;
  private final StringWriter err;
  private final int exitValue;

  private List<String> outLines;
  private List<String> errLines;
  private String warnings;

  public HgCommandResult(StringWriter out, StringWriter err, int exitValue) {
    this.out = out;
    this.err = err;
    this.exitValue = exitValue;
  }

  public List<String> getOutputLines() {
    if (outLines == null) {
      outLines = Arrays.asList(LineTokenizer.tokenize(out.getBuffer(), false));
    }
    return outLines;
  }

  public List<String> getErrorLines() {
    if (errLines == null) {
      errLines = Arrays.asList(LineTokenizer.tokenize(err.getBuffer(), false));
    }
    return errLines;
  }

  public String getRawOutput() {
    return out.toString();
  }
  
  public String getRawError() {
    return err.toString();
  }

  public int getExitValue() {
    return exitValue;
  }

  void setWarnings(String warnings) {
    this.warnings = warnings;
  }

  public String getWarnings() {
    return warnings;
  }
}
