package com.intellij.json.formatter;

import com.intellij.codeInsight.editorActions.enter.EnterBetweenBracesHandler;

/**
 * @author Mikhail Golubev
 */
public class JsonEnterBetweenBracesHandler extends EnterBetweenBracesHandler {
  @Override
  protected boolean isBracePair(char c1, char c2) {
    return (c1 == '{' && c2 == '}') || (c1 == '[' && c2 == ']');
  }
}
