// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.util.UserNameRegex;
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

import static java.util.Collections.*;

public abstract class VcsLogUserFilterTest {
  @NotNull protected final Project myProject;
  @NotNull protected final VcsLogProvider myLogProvider;
  @NotNull protected final VcsLogObjectsFactory myObjectsFactory;

  public VcsLogUserFilterTest(@NotNull VcsLogProvider logProvider, @NotNull Project project) {
    myProject = project;
    myLogProvider = logProvider;
    myObjectsFactory = ServiceManager.getService(myProject, VcsLogObjectsFactory.class);
  }

  /*
  Test for IDEA-141382, IDEA-121827, IDEA-141158
   */
  public void testWeirdNames() throws Exception {
    MultiMap<VcsUser, String> commits =
      generateHistory("User [company]", "user@company.com", "Userovich, User", "userovich@company.com", "User (user)",
                      "useruser@company.com");
    List<VcsCommitMetadata> metadata = generateMetadata(commits);

    StringBuilder builder = new StringBuilder();
    for (VcsUser user : commits.keySet()) {
      checkFilterForUser(user, commits.keySet(), commits.get(user), metadata, builder);
    }
    assertFilteredCorrectly(builder);
  }

  public void testWeirdCharacters() throws Exception {
    List<String> names = ContainerUtil.newArrayList();

    for (Character c : UserNameRegex.EXTENDED_REGEX_CHARS) {
      String name = "user" + c + "userovich" + c.hashCode(); // hashCode is required so that uses wont be synonyms
      names.add(name);
      names.add(name + "@company.com");
    }

    MultiMap<VcsUser, String> commits = generateHistory(ArrayUtil.toStringArray(names));
    List<VcsCommitMetadata> metadata = generateMetadata(commits);

    StringBuilder builder = new StringBuilder();
    for (VcsUser user : commits.keySet()) {
      checkFilterForUser(user, commits.keySet(), commits.get(user), metadata, builder);
    }
    assertFilteredCorrectly(builder);
  }

  public void testFullMatching() throws Exception {
    VcsUser nik = myObjectsFactory.createUser("nik", "nik@company.com");
    List<VcsUser> users = Arrays.asList(nik,
                                        myObjectsFactory.createUser("Chainik", "chainik@company.com"),
                                        myObjectsFactory.createUser("Nik Fury", "nikfury@company.com"),
                                        myObjectsFactory.createUser("nikniknik", "nikniknik@company.com"));

    MultiMap<VcsUser, String> commits = generateHistory(users);
    List<VcsCommitMetadata> metadata = generateMetadata(commits);
    StringBuilder builder = new StringBuilder();
    checkFilterForUser(nik, commits.keySet(), commits.get(nik), metadata, builder);
    assertFilteredCorrectly(builder);
  }

  public void testSynonyms(@NotNull Set<Character> excludes) throws Exception {
    List<String> names = ContainerUtil.newArrayList();

    Set<String> synonyms = ContainerUtil.newHashSet();
    for (char c = ' '; c <= '~'; c++) {
      if (c == '\'' || c == '!' || c == '\\' || Character.isUpperCase(c) || excludes.contains(c)) continue;
      String name = "User" + c + "Userovich";
      names.add(name);
      names.add(name + "@company.com");
      if (!Character.isLetterOrDigit(c)) synonyms.add(name);
    }
    names.add("User Userovich Userov");
    names.add("UserUserovich@company.com");

    MultiMap<VcsUser, String> commits = generateHistory(ArrayUtil.toStringArray(names));
    List<VcsCommitMetadata> metadata = generateMetadata(commits);

    List<String> synonymCommits = ContainerUtil.newArrayList();
    for (VcsUser user : commits.keySet()) {
      if (synonyms.contains(user.getName())) synonymCommits.addAll(commits.get(user));
    }

    StringBuilder builder = new StringBuilder();
    for (VcsUser user : commits.keySet()) {
      if (synonyms.contains(user.getName())) {
        checkFilterForUser(user, commits.keySet(), synonymCommits, metadata, builder);
      }
      else {
        checkFilterForUser(user, commits.keySet(), commits.get(user), metadata, builder);
      }
    }
    assertFilteredCorrectly(builder);
  }


  /*
  Turkish character İ corresponds to lower case i, while I is ı.
  But since we ca not find locale by username, this test it incorrect.
  Currently we do not lower-case non-ascii letters at all (works incorrectly for them without the locale), so we do not find synonyms for names with İ and ı.
  And for I and i incorrect synonyms are found (since we assume that I is upper-case for i).
   */
  public void testTurkishLocale() throws Exception {
    VcsUser upperCaseDotUser = myObjectsFactory.createUser("\u0130name", "uppercase.dot@company.com");
    VcsUser lowerCaseDotUser = myObjectsFactory.createUser("\u0069name", "lowercase.dot@company.com");
    VcsUser upperCaseDotlessUser = myObjectsFactory.createUser("\u0049name", "uppercase.dotless@company.com");
    VcsUser lowerCaseDotlessUser = myObjectsFactory.createUser("\u0131name", "lowercase.dotless@company.com");

    List<VcsUser> users = Arrays.asList(upperCaseDotUser, lowerCaseDotUser, upperCaseDotlessUser, lowerCaseDotlessUser);

    MultiMap<VcsUser, String> commits = generateHistory(users);
    List<VcsCommitMetadata> metadata = generateMetadata(commits);

    StringBuilder builder = new StringBuilder();

    checkTurkishAndEnglishLocales(upperCaseDotUser, emptySet(), commits, metadata, builder);
    checkTurkishAndEnglishLocales(lowerCaseDotlessUser, emptySet(), commits, metadata, builder);
    checkTurkishAndEnglishLocales(lowerCaseDotUser, singleton(upperCaseDotlessUser), commits, metadata, builder);
    checkTurkishAndEnglishLocales(upperCaseDotlessUser, singleton(lowerCaseDotUser), commits, metadata, builder);

    assertFilteredCorrectly(builder);
  }

  private void checkTurkishAndEnglishLocales(@NotNull VcsUser user,
                                             @NotNull Collection<? extends VcsUser> synonymUsers,
                                             @NotNull MultiMap<VcsUser, String> commits,
                                             @NotNull List<? extends VcsCommitMetadata> metadata, @NotNull StringBuilder builder)
    throws VcsException {
    Set<String> expectedCommits = ContainerUtil.newHashSet(commits.get(user));
    for (VcsUser synonym : synonymUsers) {
      expectedCommits.addAll(commits.get(synonym));
    }

    Locale oldLocale = Locale.getDefault();
    Locale.setDefault(new Locale("tr"));
    StringBuilder turkishBuilder = new StringBuilder();
    checkFilterForUser(user, commits.keySet(), expectedCommits, metadata, turkishBuilder);

    Locale.setDefault(Locale.ENGLISH);
    StringBuilder defaultBuilder = new StringBuilder();
    checkFilterForUser(user, commits.keySet(), expectedCommits, metadata, defaultBuilder);
    Locale.setDefault(oldLocale);

    if (!turkishBuilder.toString().isEmpty()) builder.append("Turkish Locale:\n").append(turkishBuilder);
    if (!defaultBuilder.toString().isEmpty()) builder.append("English Locale:\n").append(defaultBuilder);
  }

  /*
  Test for IDEA-152545
   */
  public void testJeka() throws Exception {
    VcsUser jeka = myObjectsFactory.createUser("User Userovich", "jeka@company.com");
    List<VcsUser> users = Arrays.asList(jeka,
                                        myObjectsFactory.createUser("Auser Auserovich", "auser@company.com"),
                                        myObjectsFactory.createUser("Buser Buserovich", "buser@company.com"),
                                        myObjectsFactory.createUser("Cuser cuserovich", "cuser@company.com"));

    MultiMap<VcsUser, String> commits = generateHistory(users);
    List<VcsCommitMetadata> metadata = generateMetadata(commits);
    StringBuilder builder = new StringBuilder();
    VcsLogUserFilter userFilter = VcsLogFilterObject.fromUserNames(singleton("jeka"), emptyMap(), commits.keySet());
    checkFilter(userFilter, "jeka", commits.get(jeka), metadata, builder);
    assertFilteredCorrectly(builder);
  }

  private void checkFilterForUser(@NotNull VcsUser user,
                                  @NotNull Set<? extends VcsUser> allUsers,
                                  @NotNull Collection<String> expectedHashes,
                                  @NotNull List<? extends VcsCommitMetadata> metadata, @NotNull StringBuilder errorMessageBuilder)
    throws VcsException {
    VcsLogUserFilter userFilter = VcsLogFilterObject.fromUser(user, allUsers);
    checkFilter(userFilter, user.toString(), expectedHashes, metadata, errorMessageBuilder);
  }

  private void checkFilter(VcsLogUserFilter userFilter,
                           String filterDescription,
                           @NotNull Collection<String> expectedHashes,
                           @NotNull List<? extends VcsCommitMetadata> metadata, @NotNull StringBuilder errorMessageBuilder) throws VcsException {
    // filter by vcs
    List<String> actualHashes = getFilteredHashes(userFilter);

    if (!hasSameElements(expectedHashes, actualHashes)) {
      errorMessageBuilder.append(TestCase.format("VCS filter for: " + filterDescription, expectedHashes, actualHashes)).append("\n");
    }

    // filter in memory
    actualHashes = getFilteredHashes(userFilter, metadata);
    if (!hasSameElements(expectedHashes, actualHashes)) {
      errorMessageBuilder.append(TestCase.format("Memory filter for: " + filterDescription, expectedHashes, actualHashes)).append("\n");
    }
  }

  private static <T> boolean hasSameElements(@NotNull Collection<? extends T> collection, @NotNull Collection<T> expected) {
    return ContainerUtil.newHashSet(expected).equals(ContainerUtil.newHashSet(collection));
  }

  @NotNull
  private List<String> getFilteredHashes(@NotNull VcsLogUserFilter filter) throws VcsException {
    VcsLogFilterCollection filters = VcsLogFilterObject.collection(filter);
    List<TimedVcsCommit> commits = myLogProvider.getCommitsMatchingFilter(myProject.getBaseDir(), filters, -1);
    return ContainerUtil.map(commits, commit -> commit.getId().asString());
  }

  @NotNull
  private static List<String> getFilteredHashes(@NotNull VcsLogUserFilter filter, @NotNull List<? extends VcsCommitMetadata> metadata) {
    return ContainerUtil.map(ContainerUtil.filter(metadata, filter::matches), metadata1 -> metadata1.getId().asString());
  }

  @NotNull
  private List<VcsCommitMetadata> generateMetadata(@NotNull MultiMap<VcsUser, String> commits) {
    List<VcsCommitMetadata> result = ContainerUtil.newArrayList();

    for (VcsUser user : commits.keySet()) {
      for (String commit : commits.get(user)) {
        result.add(myObjectsFactory.createCommitMetadata(HashImpl.build(commit), emptyList(), System.currentTimeMillis(),
                                                         myProject.getBaseDir(), "subject " + Math.random(), user.getName(),
                                                         user.getEmail(), "message " + Math.random(), user.getName(), user.getEmail(),
                                                         System.currentTimeMillis()));
      }
    }

    return result;
  }

  @NotNull
  private MultiMap<VcsUser, String> generateHistory(String... names) throws IOException {
    TestCase.assertTrue("Incorrect user names (should be pairs of users and emails) " + Arrays.toString(names), names.length % 2 == 0);

    List<VcsUser> users = ContainerUtil.newArrayList();
    for (int i = 0; i < names.length / 2; i++) {
      users.add(myObjectsFactory.createUser(names[2 * i], names[2 * i + 1]));
    }

    return generateHistory(users);
  }

  @NotNull
  private MultiMap<VcsUser, String> generateHistory(@NotNull List<? extends VcsUser> users) throws IOException {
    MultiMap<VcsUser, String> commits = MultiMap.createLinked();

    for (VcsUser user : users) {
      recordCommit(commits, user);
    }

    VfsUtil.markDirtyAndRefresh(false, true, false, myProject.getBaseDir());
    return commits;
  }

  private static void assertFilteredCorrectly(@NotNull StringBuilder builder) {
    TestCase.assertTrue("Incorrectly filtered log for\n" + builder.toString(), builder.toString().isEmpty());
  }

  private void recordCommit(@NotNull MultiMap<VcsUser, String> commits, @NotNull VcsUser user) throws IOException {
    String commit = commit(user);
    commits.putValue(user, commit);
  }

  @NotNull
  protected abstract String commit(VcsUser user) throws IOException;
}
