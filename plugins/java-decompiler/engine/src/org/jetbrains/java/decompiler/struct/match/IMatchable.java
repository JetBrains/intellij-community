package org.jetbrains.java.decompiler.struct.match;


public interface IMatchable {

  public enum MatchProperties {
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
  
  public IMatchable findObject(MatchNode matchNode, int index);

  public boolean match(MatchNode matchNode, MatchEngine engine);
  
}
