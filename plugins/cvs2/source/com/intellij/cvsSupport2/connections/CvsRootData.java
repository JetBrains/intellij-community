package com.intellij.cvsSupport2.connections;

import com.intellij.openapi.util.Comparing;

/**
 * author: lesya
 */
public class CvsRootData {
  protected final String myStringRepsentation;
  public CvsMethod METHOD = null;
  public String HOST = "";
  public String USER = "";
  public String REPOSITORY = "";
  public int PORT;
  public String PASSWORD = "";
  public String PROXY_HOST = null;
  public String PROXY_PORT = null;
  public boolean CONTAINS_PROXY_INFO = false;


  public CvsRootData(String stringRepsentation) {
    myStringRepsentation = stringRepsentation;
  }

  public String getCvsRootAsString() {
    return myStringRepsentation;
  }

  public boolean equals(Object object) {
    if (!object.getClass().equals(getClass())) return false;
    CvsConnectionSettings other = (CvsConnectionSettings)object;

    return Comparing.equal(METHOD, other.METHOD)
           && Comparing.equal(HOST, other.HOST)
           && Comparing.equal(USER, other.USER)
           && Comparing.equal(REPOSITORY, other.REPOSITORY)
           && PORT == other.PORT;
  }

  public int hashCode() {
    int methodHashCode = METHOD == null ? 0 : METHOD.hashCode();
    return methodHashCode ^ HOST.hashCode() ^ USER.hashCode() ^ REPOSITORY.hashCode() ^ PORT;
  }
}
