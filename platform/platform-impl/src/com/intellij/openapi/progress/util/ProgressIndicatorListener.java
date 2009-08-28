package com.intellij.openapi.progress.util;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 19, 2004
 * Time: 5:15:09 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ProgressIndicatorListener {
  public void cancelled();

  public void stopped();
}
