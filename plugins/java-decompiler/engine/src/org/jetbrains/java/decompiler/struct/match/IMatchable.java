/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.struct.match;


public interface IMatchable {

  enum MatchProperties {
    STATEMENT_TYPE,
    STATEMENT_RET,
    STATEMENT_STATSIZE,
    STATEMENT_EXPRSIZE,
    STATEMENT_POSITION,
    STATEMENT_IFTYPE,
    
    EXPRENT_TYPE,
    EXPRENT_RET,
    EXPRENT_POSITION,
    EXPRENT_FUNCTYPE,
    EXPRENT_EXITTYPE,
    EXPRENT_CONSTTYPE,
    EXPRENT_CONSTVALUE,
    EXPRENT_INVOCATION_CLASS,
    EXPRENT_INVOCATION_SIGNATURE,
    EXPRENT_INVOCATION_PARAMETER,
    EXPRENT_VAR_INDEX,
    EXPRENT_FIELD_NAME,
  }
  
  IMatchable findObject(MatchNode matchNode, int index);

  boolean match(MatchNode matchNode, MatchEngine engine);
  
}
