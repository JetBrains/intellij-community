/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.intellij.lang.ant.dom;

import java.io.File;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

/**
 * A Path tokenizer takes a path and returns the components that make up
 * that path.
 * <p>
 * The path can use path separators of either ':' or ';' and file separators
 * of either '/' or '\'.
 */
public class PathTokenizer {
  /**
   * A tokenizer to break the string up based on the ':' or ';' separators.
   */
  private StringTokenizer tokenizer;

  /**
   * A String which stores any path components which have been read ahead
   * due to DOS filesystem compensation.
   */
  private String lookahead = null;

  /**
   * Flag to indicate whether or not we are running on a platform with a
   * DOS style filesystem
   */
  private boolean dosStyleFilesystem;

  /**
   * Constructs a path tokenizer for the specified path.
   *
   * @param path The path to tokenize. Must not be <code>null</code>.
   */
  public PathTokenizer(String path) {
    // on Windows and Unix, we can ignore delimiters and still have
    // enough information to tokenize correctly.
    tokenizer = new StringTokenizer(path, ":;", false);
    dosStyleFilesystem = File.pathSeparatorChar == ';';
  }

  /**
   * Tests if there are more path elements available from this tokenizer's
   * path. If this method returns <code>true</code>, then a subsequent call
   * to nextToken will successfully return a token.
   *
   * @return <code>true</code> if and only if there is at least one token
   * in the string after the current position; <code>false</code> otherwise.
   */
  public boolean hasMoreTokens() {
    return lookahead != null || tokenizer.hasMoreTokens();
  }

  /**
   * Returns the next path element from this tokenizer.
   *
   * @return the next path element from this tokenizer.
   * @throws NoSuchElementException if there are no more elements in this
   *                                tokenizer's path.
   */
  public String nextToken() throws NoSuchElementException {
    String token;
    if (lookahead != null) {
      token = lookahead;
      lookahead = null;
    }
    else {
      token = tokenizer.nextToken().trim();
    }

    if (token.length() == 1 && Character.isLetter(token.charAt(0))
        && dosStyleFilesystem
        && tokenizer.hasMoreTokens()) {
      // we are on a dos style system so this path could be a drive
      // spec. We look at the next token
      String nextToken = tokenizer.nextToken().trim();
      if (nextToken.startsWith("\\") || nextToken.startsWith("/")) {
        // we know we are on a DOS style platform and the next path
        // starts with a slash or backslash, so we know this is a
        // drive spec
        token += ":" + nextToken;
      }
      else {
        // store the token just read for next time
        lookahead = nextToken;
      }
    }
    return token;
  }
}
