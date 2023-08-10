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
      {"http://", "protocol", "scheme"},
      {"file:///", "protocol", "scheme"},
      {"/etc/passwd", "path", "filePath", "fileName"},
      {"image.jpg", "image", "img", "picture"},
      {"PICTURE.PNG", "image", "img", "picture"},
      {"movie.mp4", "video", "movie"},
      {"test.txt", "file", "fileName", "text"},
      {"application/json", "contentType", "mediaType", "mimeType"},
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
      {"2012/2/3", "date"},
      {"23:59", "time"},
      {"7:00:00", "time"},
      {"c3499c2729730aaff07efb8676a92dcb6f8a3f8f", "sha1", "hash", "key", "secret", "token"},
      {"50d858e0985ecc7f60418aaf0cc5ab587f42c2570a884095a9e8ccacd0f6545c", "sha256", "hash", "key", "secret", "token"},
      {"sha384-HSMxcRTRxnN+Bdg0JdbxYKrThecOKuH5zCYotlSAcp1+c8xmyTe9GYg1l9a69psu", "sha384", "hash", "key", "secret", "token"},
      {"fe5ec832d75fd78ec517f33a181259bb953dc54aa8a6332ba0800172c64b18e92b362e0188891efcdb3e3c7d4fb3c14b", "sha384", "hash", "key", "secret", "token"},
      {"sha512-j0Q2QD83DuJB4LcfsSAoUINhA5gmZsrBgwxiYMXYhrRH+kwU09Ht3qKGuf+7WbaWnxDYOI0KiKKZ+HgshPR/fw==", "sha512", "hash", "key", "secret", "token"},
      {"a65a5831694db0d4ef143cfabce59d4a55c19999e308b34f642e93ccb17bd2cd45642e255d9ecc517c5565a874daa0f903b006d5fa905d158cee43adb28254fa", "sha512", "hash", "key", "secret", "token"},
    };
    for (String[] test : data) {
      List<String> strings = LiteralNameSuggester.literalNames(test[0]);
      assertEquals(test[0], Arrays.asList(test).subList(1, test.length), strings);
    }
  }
}
