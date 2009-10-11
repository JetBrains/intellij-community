/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.compiler.generator;

import com.intellij.openapi.compiler.ValidityState;
import com.intellij.openapi.diagnostic.Logger;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 20.08.2007
 */
class TopLevelDependencyValidityState implements ValidityState {
  private final long myTimestamp;
  private final List<String> myMembers;  //fields

  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.compiler.generator.TopLevelDependencyValidityState");

  TopLevelDependencyValidityState(long timestamp, List<String> members) {
//      use signature of method and access modifiers
    this.myMembers = members;
    myTimestamp = timestamp;
  }

  public boolean equalsTo(ValidityState validityState) {
    if (!(validityState instanceof TopLevelDependencyValidityState)) return false;

    return ((TopLevelDependencyValidityState) validityState).myTimestamp == this.myTimestamp
        && myMembers.equals(((TopLevelDependencyValidityState) validityState).myMembers);
  }

  public void save(DataOutput out) throws IOException {
    out.writeLong(myTimestamp);
    out.writeChar('\n');

    for (String member : myMembers) {
      out.writeChar('\n');
      out.writeUTF(member);
    }
  }

  public static TopLevelDependencyValidityState load(DataInputStream is) throws IOException {
    long timestamp = -1;

    Reader reader = new InputStreamReader(is);
    StreamTokenizer tokenizer = new StreamTokenizer(reader);
//    tokenizer.whitespaceChars(' ', ' ');
//    tokenizer.whitespaceChars('\t', '\t');
//    tokenizer.whitespaceChars('\f', '\f');
//    tokenizer.whitespaceChars('\n', '\n');
//    tokenizer.whitespaceChars('\r', '\r');

    List<String> members = new ArrayList<String>();
    while (true) {
      int ttype = tokenizer.nextToken();
      switch (ttype) {
        case StreamTokenizer.TT_NUMBER: {
          try {
            timestamp = (long) tokenizer.nval;
          } catch (NumberFormatException e) {
            LOG.error(e);
          }
          break;
        }
        case StreamTokenizer.TT_WORD: {
          members.add(tokenizer.sval);
          break;
        }
        case StreamTokenizer.TT_EOL:
          break;
        case StreamTokenizer.TT_EOF:
        default:
          break;
      }
      if (ttype == StreamTokenizer.TT_EOF)
        break;
    }
    return new TopLevelDependencyValidityState(timestamp, members);
  }
}
