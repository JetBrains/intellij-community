// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.tasks.CommitPlaceholderProvider;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.Matcher;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNullElse;

/**
 * @author Dmitry Avdeev
 */
public final class TaskUtil {
  // Almost ISO-8601 strict, except that date components may be separated by '/', parts may be adjoined with space,
  // and date-only strings are also allowed, just in case.
  private static final Pattern ISO8601_DATE_PATTERN = Pattern.compile(
    "(\\d{4}[/-]\\d{2}[/-]\\d{2})" +                   // date (1)
    "(?:[ T]" +
    "(\\d{2}:\\d{2}:\\d{2})(.\\d{3,})?" +              // optional time (2) and milliseconds (3)
    "(?:\\s?" +
    "([+-]\\d{2}:\\d{2}|[+-]\\d{4}|[+-]\\d{2}|Z)" +    // optional timezone info (4), if time is also present
    ")?)?"
  );

  private TaskUtil() {
    // empty
  }

  public static String formatTask(@NotNull Task task, String format) {
    Map<String, String> map = formatFromExtensions(task instanceof LocalTask ? (LocalTask)task : new LocalTaskImpl(task));
    if (!task.isIssue()) {
      map.put("id", ""); // clear fake id
    }
    format = updateToVelocity(format);
    return FileTemplateUtil.mergeTemplate(map, format, false).trim();
  }

  private static Map<String, String> formatFromExtensions(@NotNull LocalTask task) {
    Map<String, String> map = new HashMap<>();
    for (CommitPlaceholderProvider extension : CommitPlaceholderProvider.EXTENSION_POINT_NAME.getExtensionList()) {
      String[] placeholders = extension.getPlaceholders(task.getRepository());
      for (String placeholder : placeholders) {
        String value = extension.getPlaceholderValue(task, placeholder);
        if (value != null) {
          map.put(placeholder, value);
        }
      }
    }
    return map;
  }

  public static String getChangeListComment(Task task) {
    return getChangeListComment(task, false);
  }

  public static @Nullable String getChangeListComment(Task task, boolean forCommit) {
    final TaskRepository repository = task.getRepository();
    if (repository == null || !repository.isShouldFormatCommitMessage()) {
      return null;
    }
    return formatTask(task, repository.getCommitMessageFormat());
  }

  public static @Nls String getTrimmedSummary(Task task) {
    String text;
    if (task.isIssue()) {
      text = task.getPresentableId() + ": " + task.getSummary();
    }
    else {
      text = task.getSummary();
    }
    return StringUtil.first(text, 60, true);
  }

  public static @Nullable Date parseDate(@NotNull String s) {
    s = s.replace('/', '-');

    try {
      return Date.from(Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(s)));
    }
    catch (DateTimeParseException ignored) { }

    var matcher = ISO8601_DATE_PATTERN.matcher(s);
    if (matcher.matches()) {
      var date = matcher.group(1).replace('/', '-');
      var time = requireNonNullElse(matcher.group(2), "00:00:00");
      var millis = requireNonNullElse(matcher.group(3), ".000").substring(1);
      var timezone = requireNonNullElse(matcher.group(4), "Z");
      if (timezone.length() == 5) {
        // [+-]HHmm, missing colon
        timezone = timezone.substring(0, 3) + ':' + timezone.substring(3);
      }
      s = String.format("%sT%s.%s%s", date, time, millis, timezone);
      try {
        return Date.from(Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(s)));
      }
      catch (DateTimeParseException ignored) { }
    }

    return null;
  }

  public static String formatDate(@NotNull Date date) {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(date.toInstant().atZone(ZoneOffset.UTC));
  }

  /**
   * {@link Task#equals(Object)} implementation compares tasks by their unique IDs only.
   * This method should be used when full comparison is necessary.
   */
  public static boolean tasksEqual(@NotNull Task t1, @NotNull Task t2) {
    if (!t1.getId().equals(t2.getId())) return false;
    if (!t1.getSummary().equals(t2.getSummary())) return false;
    if (t1.isClosed() != t2.isClosed()) return false;
    if (t1.isIssue() != t2.isIssue()) return false;
    if (!Comparing.equal(t1.getState(), t2.getState())) return false;
    if (!Comparing.equal(t1.getType(), t2.getType())) return false;
    if (!Objects.equals(t1.getDescription(), t2.getDescription())) return false;
    if (!Comparing.equal(t1.getCreated(), t2.getCreated())) return false;
    if (!Comparing.equal(t1.getUpdated(), t2.getUpdated())) return false;
    if (!Objects.equals(t1.getIssueUrl(), t2.getIssueUrl())) return false;
    if (!Arrays.equals(t1.getComments(), t2.getComments())) return false;
    if (!Comparing.equal(t1.getIcon(), t2.getIcon())) return false;
    if (!Objects.equals(t1.getCustomIcon(), t2.getCustomIcon())) return false;
    return Comparing.equal(t1.getRepository(), t2.getRepository());
  }

  public static boolean tasksEqual(@NotNull List<? extends Task> tasks1, @NotNull List<? extends Task> tasks2) {
    if (tasks1.size() != tasks2.size()) return false;
    for (int i = 0; i < tasks1.size(); i++) {
      if (!tasksEqual(tasks1.get(i), tasks2.get(i))) {
        return false;
      }
    }
    return true;
  }

  public static boolean tasksEqual(Task @NotNull [] task1, Task @NotNull [] task2) {
    return tasksEqual(Arrays.asList(task1), Arrays.asList(task2));
  }

  /**
   * Parse and print pretty-formatted XML to {@code logger}, if its level is DEBUG or below.
   */
  public static void prettyFormatXmlToLog(@NotNull Logger logger, @NotNull String xml) {
    if (logger.isDebugEnabled()) {
      try {
        logger.debug("\n" + JDOMUtil.write(JDOMUtil.load(xml)));
      }
      catch (Exception e) {
        logger.debug(e);
      }
    }
  }

  /**
   * Parse and print pretty-formatted Json to {@code logger}, if its level is DEBUG or below.
   */
  public static void prettyFormatJsonToLog(@NotNull Logger logger, @NotNull String json) {
    if (logger.isDebugEnabled()) {
      try {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        logger.debug("\n" + gson.toJson(gson.fromJson(json, JsonElement.class)));
      }
      catch (JsonSyntaxException e) {
        logger.debug("Malformed JSON\n" + json);
      }
    }
  }

  /**
   * Perform standard {@code application/x-www-urlencoded} translation for string {@code s}.
   *
   * @return urlencoded string
   */
  public static @NotNull String encodeUrl(@NotNull String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  public static @Unmodifiable List<Task> filterTasks(final String pattern, final List<? extends Task> tasks) {
    final Matcher matcher = getMatcher(pattern);
    return ContainerUtil.mapNotNull(tasks,
                                    task -> matcher.matches(task.getPresentableId()) || matcher.matches(task.getSummary()) ? task : null);
  }

  private static Matcher getMatcher(String pattern) {
    StringTokenizer tokenizer = new StringTokenizer(pattern, " ");
    StringBuilder builder = new StringBuilder();
    while (tokenizer.hasMoreTokens()) {
      String word = tokenizer.nextToken();
      builder.append('*');
      builder.append(word);
      builder.append("* ");
    }

    return NameUtil.buildMatcher(builder.toString(), NameUtil.MatchingCaseSensitivity.NONE);
  }

  static String updateToVelocity(String format) {
    return format.replaceAll("\\{", "\\$\\{").replaceAll("\\$\\$\\{", "\\$\\{");
  }
}
