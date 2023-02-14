// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text;

import com.intellij.openapi.util.text.LiteralNameSuggester;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LiteralNameSuggesterTest {
  @Test
  public void testLiteralNameSuggester() {
    String[][] data = {
      {"http://xyz", "url"},
      {"https://xyz", "url"},
      {"http://", "protocol"},
      {"file:///", "protocol"},
      {"/etc/passwd", "path", "filePath", "fileName"},
      {"image.jpg", "image", "img"},
      {"PICTURE.PNG", "image", "img"},
      {"movie.mp4", "video"},
      {"test.txt", "file", "fileName", "text"},
      {"application/json", "contentType"},
      {" ", "space", "indent"},
      {"  ", "spaces", "indent"},
      {"   ", "spaces", "indent"},
      {"\t", "tab", "indent"},
      {"\t\t", "tabs", "indent"},
      {"\n", "lineBreak", "lineFeed", "lf"},
      {"\r", "lineBreak", "cr"},
      {"\r\n", "crlf"},
      {"support@example.com", "mail", "email"},
      {"123", "number", "id"},
      {"123e4567-e89b-12d3-a456-426614174000", "uuid", "guid"},
      {"SELECT * FROM test", "query", "sql"},
      {"2012-12-20", "date"},
      {"2012/2/3", "date"}
    };
    for (String[] test : data) {
      List<String> strings = LiteralNameSuggester.literalNames(test[0]);
      assertEquals(test[0], strings, Arrays.asList(test).subList(1, test.length));
    }
  }
}
