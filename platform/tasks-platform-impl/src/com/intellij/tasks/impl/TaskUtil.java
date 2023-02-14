// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.text.DateFormatUtil;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Dmitry Avdeev
 */
public final class TaskUtil {

  // Almost ISO-8601 strict except date parts may be separated by '/'
  // and date only also allowed just in case
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
    return FileTemplateUtil.mergeTemplate(map, format, false);
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
    // SimpleDateFormat prior JDK7 doesn't support 'X' specifier for ISO 8601 timezone format.
    // Because some bug trackers and task servers e.g. send dates ending with 'Z' (that stands for UTC),
    // dates should be preprocessed before parsing.
    Matcher m = ISO8601_DATE_PATTERN.matcher(s);
    if (!m.matches()) {
      return null;
    }
    String datePart = m.group(1).replace('/', '-');
    String timePart = m.group(2);
    if (timePart == null) {
      timePart = "00:00:00";
    }
    String milliseconds = m.group(3);
    milliseconds = milliseconds == null ? "000" : milliseconds.substring(1, 4);
    String timezone = m.group(4);
    if (timezone == null || timezone.equals("Z")) {
      timezone = "+0000";
    }
    else if (timezone.length() == 3) {
      // [+-]HH
      timezone += "00";
    }
    else if (timezone.length() == 6) {
      // [+-]HH:MM
      timezone = timezone.substring(0, 3) + timezone.substring(4, 6);
    }
    String canonicalForm = String.format("%sT%s.%s%s", datePart, timePart, milliseconds, timezone);
    try {
      return DateFormatUtil.getIso8601Format().parse(canonicalForm);
    }
    catch (ParseException e) {
      return null;
    }
  }

  public static String formatDate(@NotNull Date date) {
    return DateFormatUtil.getIso8601Format().format(date);
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
   * Print pretty-formatted XML to {@code logger}, if its level is DEBUG or below.
   */
  public static void prettyFormatXmlToLog(@NotNull Logger logger, @NotNull Element element) {
    if (logger.isDebugEnabled()) {
      // alternatively
      //new XMLOutputter(Format.getPrettyFormat()).outputString(root)
      logger.debug("\n" + JDOMUtil.createOutputter("\n").outputString(element));
    }
  }

  /**
   * Parse and print pretty-formatted XML to {@code logger}, if its level is DEBUG or below.
   */
  public static void prettyFormatXmlToLog(@NotNull Logger logger, @NotNull InputStream xml) {
    if (logger.isDebugEnabled()) {
      try {
        logger.debug("\n" + JDOMUtil.createOutputter("\n").outputString(JDOMUtil.load(xml)));
      }
      catch (Exception e) {
        logger.debug(e);
      }
    }
  }

  /**
   * Parse and print pretty-formatted XML to {@code logger}, if its level is DEBUG or below.
   */
  public static void prettyFormatXmlToLog(@NotNull Logger logger, @NotNull String xml) {
    if (logger.isDebugEnabled()) {
      try {
        logger.debug("\n" + JDOMUtil.createOutputter("\n").outputString(JDOMUtil.load(xml)));
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
   * Parse and print pretty-formatted Json to {@code logger}, if its level is DEBUG or below.
   */
  public static void prettyFormatJsonToLog(@NotNull Logger logger, @NotNull JsonElement json) {
    if (logger.isDebugEnabled()) {
      try {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        logger.debug("\n" + gson.toJson(json));
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

  public static List<Task> filterTasks(final String pattern, final List<? extends Task> tasks) {
    final com.intellij.util.text.Matcher matcher = getMatcher(pattern);
    return ContainerUtil.mapNotNull(tasks,
                                    task -> matcher.matches(task.getPresentableId()) || matcher.matches(task.getSummary()) ? task : null);
  }

  private static com.intellij.util.text.Matcher getMatcher(String pattern) {
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
