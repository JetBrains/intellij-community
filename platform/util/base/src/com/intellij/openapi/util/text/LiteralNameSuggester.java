// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.text;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A utility class to suggest variable names for literals based on their content
 */
@Internal // consider integrating into NameUtilCore
public final class LiteralNameSuggester {
  private static final class PatternBasedSuggestions {
    private final @NotNull Pattern pattern;
    private final @NotNull List<@NotNull String> names;

    PatternBasedSuggestions(@NotNull @Language("regexp") String pattern, @NotNull String @NotNull ... names) {
      this.pattern = Pattern.compile(pattern);
      this.names = Arrays.asList(names);
    }
  }
  
  private static final PatternBasedSuggestions[] ourPatternBasedSuggestions = {
    new PatternBasedSuggestions("(?i)https?://|file:///", "protocol", "scheme"),
    new PatternBasedSuggestions("(?i)(https?://|file:///)\\w.*", "url"),
    new PatternBasedSuggestions("(/\\w+){2,}", "path", "filePath", "fileName"),
    new PatternBasedSuggestions("(?i)(\\w+)\\.(jpg|jpeg|gif|png|apng)", "image", "img", "picture"),
    new PatternBasedSuggestions("(?i)(\\w+)\\.(mp4|avi|mov)", "video", "movie"),
    new PatternBasedSuggestions("(?i)(\\w+)\\.txt", "file", "fileName", "text"),
    new PatternBasedSuggestions("text/plain|text/html|text/css|text/javascript|" +
                                "image/png|image/jpeg|image/gif|image/apng|image/webp|image/svg+xml|" +
                                "audio/mpeg|audio/webm|video/webm|multipart/form-data|application/json|" +
                                "application/zip|application/pdf|application/graphql", "contentType", "mediaType", "mimeType"),
    new PatternBasedSuggestions(" ", "space", "indent"),
    new PatternBasedSuggestions("#\\w+", "hashtag"),
    new PatternBasedSuggestions(" {2,}", "spaces", "indent"),
    new PatternBasedSuggestions("\t", "tab", "indent"),
    new PatternBasedSuggestions("\t{2,}", "tabs", "indent"),
    new PatternBasedSuggestions("\n", "lineBreak", "lineFeed", "lf"),
    new PatternBasedSuggestions("\r", "lineBreak", "cr"),
    new PatternBasedSuggestions("\r\n", "crlf"),
    new PatternBasedSuggestions("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}", "mail", "email"),
    new PatternBasedSuggestions("\\d+", "number", "id"),
    new PatternBasedSuggestions("(?i)[0-9a-f]{8}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{4}\\b-[0-9a-f]{12}", "uuid", "guid"),
    new PatternBasedSuggestions("SELECT\\s.*FROM\\s.*", "query", "sql"),
    new PatternBasedSuggestions("(19|20)[0-9][0-9]([-/])(0?[1-9]|1[0-2])\\2(0?[1-9]|[12][0-9]|30|31)", "date"),
    new PatternBasedSuggestions("(?:[0-1]?[0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9])?", "time"),
    new PatternBasedSuggestions("[0-9a-fA-F]{40}", "sha1", "hash", "key", "secret", "token"),
    new PatternBasedSuggestions("[0-9a-fA-F]{64}", "sha256", "hash", "key", "secret", "token"),
    new PatternBasedSuggestions("[0-9a-fA-F]{96}|sha384-[A-Za-z0-9+=/]{64}", "sha384", "hash", "key", "secret", "token"),
    new PatternBasedSuggestions("[0-9a-fA-F]{128}|sha512-[A-Za-z0-9+=/]{88}", "sha512", "hash", "key", "secret", "token"),
  };

  /**
   * @param literalValue string literal value
   * @return suggested names
   */
  public static @Unmodifiable List<String> literalNames(String literalValue) {
    return Arrays.stream(ourPatternBasedSuggestions)
      .filter(suggestion -> suggestion.pattern.matcher(literalValue).matches())
      .flatMap(suggestion -> suggestion.names.stream()).collect(Collectors.toList());
  }
}
