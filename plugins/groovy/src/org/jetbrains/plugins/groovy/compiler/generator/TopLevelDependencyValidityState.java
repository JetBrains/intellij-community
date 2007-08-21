package org.jetbrains.plugins.groovy.compiler.generator;

import com.intellij.openapi.compiler.ValidityState;

import java.util.List;
import java.util.ArrayList;
import java.io.*;

/**
 * User: Dmitry.Krasilschikov
* Date: 20.08.2007
*/
class TopLevelDependencyValidityState implements ValidityState {
  private long myTimestamp;
  private List<String> myMembers;  //fields

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

  public void save(DataOutputStream os) throws IOException {
    os.writeLong(myTimestamp);
    os.writeChar('\n');

    for (String member : myMembers) {
      os.writeChar('\n');
      os.writeUTF(member);
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
            e.printStackTrace();
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
