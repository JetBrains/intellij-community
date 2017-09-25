/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.Convertor;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubFullPath;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.plugins.github.util.GithubUrlUtil.*;

/**
 * @author Aleksey Pivovarov
 */
public class GithubUrlUtilTest extends TestCase {
  private static class TestCase<T> {
    @NotNull final public List<Pair<String, T>> tests = new ArrayList<>();

    public void add(@NotNull String in, @Nullable T out) {
      tests.add(Pair.create(in, out));
    }

  }

  private static <T> void runTestCase(@NotNull TestCase<T> tests, @NotNull Convertor<String, T> func) {
    for (Pair<String, T> test : tests.tests) {
      assertEquals(test.getFirst(), test.getSecond(), func.convert(test.getFirst()));
    }
  }

  public void testRemoveTrailingSlash() {
    TestCase<String> tests = new TestCase<>();

    tests.add("http://github.com/", "http://github.com");
    tests.add("http://github.com", "http://github.com");

    tests.add("http://github.com/user/repo/", "http://github.com/user/repo");
    tests.add("http://github.com/user/repo", "http://github.com/user/repo");

    runTestCase(tests, in -> removeTrailingSlash(in));
  }

  public void testRemoveProtocolPrefix() {
    TestCase<String> tests = new TestCase<>();

    tests.add("github.com/user/repo/", "github.com/user/repo/");
    tests.add("api.github.com/user/repo/", "api.github.com/user/repo/");

    tests.add("http://github.com/user/repo/", "github.com/user/repo/");
    tests.add("https://github.com/user/repo/", "github.com/user/repo/");
    tests.add("git://github.com/user/repo/", "github.com/user/repo/");
    tests.add("git@github.com:user/repo/", "github.com/user/repo/");

    tests.add("git@github.com:username/repo/", "github.com/username/repo/");
    tests.add("https://username:password@github.com/user/repo/", "github.com/user/repo/");
    tests.add("https://username@github.com/user/repo/", "github.com/user/repo/");
    tests.add("https://github.com:2233/user/repo/", "github.com:2233/user/repo/");

    tests.add("HTTP://GITHUB.com/user/repo/", "GITHUB.com/user/repo/");
    tests.add("HttP://GitHub.com/user/repo/", "GitHub.com/user/repo/");

    runTestCase(tests, in -> removeProtocolPrefix(in));
  }

  public void testIsGithubUrl1() {
    TestCase<Boolean> tests = new TestCase<>();

    tests.add("http://github.com/user/repo", true);
    tests.add("https://github.com/user/repo", true);
    tests.add("git://github.com/user/repo", true);
    tests.add("git@github.com:user/repo", true);

    tests.add("https://github.com/", true);
    tests.add("github.com", true);

    tests.add("https://user@github.com/user/repo", true);
    tests.add("https://user:password@github.com/user/repo", true);
    tests.add("git@github.com:user/repo", true);

    tests.add("https://github.com:2233/", true);

    tests.add("HTTPS://GitHub.com:2233/", true);

    tests.add("google.com", false);
    tests.add("github.com.site.ua", false);
    tests.add("sf@hskfh../.#fwenj 32#$", false);
    tests.add("api.github.com", false);
    tests.add("site.com//github.com", false);

    runTestCase(tests, in -> isGithubUrl(in, "https://github.com/"));
    runTestCase(tests, in -> isGithubUrl(in, "http://GitHub.com"));
  }

  public void testIsGithubUrl2() {
    TestCase<Boolean> tests = new TestCase<>();

    tests.add("http://git.code.example.co.jp/user/repo", true);
    tests.add("https://git.code.example.co.jp/user/repo", true);
    tests.add("git://git.code.example.co.jp/user/repo", true);
    tests.add("git@git.code.example.co.jp:user/repo", true);

    tests.add("http://git.code.example.co/user/repo", false);
    tests.add("http://code.example.co.jp/user/repo", false);

    runTestCase(tests, in -> isGithubUrl(in, "git.code.example.co.jp"));
    runTestCase(tests, in -> isGithubUrl(in, "http://git.code.example.co.jp"));
    runTestCase(tests, in -> isGithubUrl(in, "https://git.code.example.co.jp/github/server"));
    runTestCase(tests, in -> isGithubUrl(in, "git.code.example.co.jp/api"));
  }

  public void testGetApiUrlWithoutProtocol() {
    TestCase<String> tests = new TestCase<>();

    tests.add("github.com", "api.github.com");
    tests.add("https://github.com/", "api.github.com");
    tests.add("api.github.com/", "api.github.com");

    tests.add("http://my.site.com/", "my.site.com/api/v3");
    tests.add("http://api.site.com/", "api.site.com/api/v3");
    tests.add("http://url.github.com/", "url.github.com/api/v3");

    tests.add("HTTP://GITHUB.com", "api.github.com");
    tests.add("HttP://GitHub.com/", "api.github.com");

    runTestCase(tests, in -> getApiUrlWithoutProtocol(in));
  }

  public void testGetUserAndRepositoryFromRemoteUrl() {
    TestCase<GithubFullPath> tests = new TestCase<>();

    tests.add("http://github.com/username/reponame/", new GithubFullPath("username", "reponame"));
    tests.add("https://github.com/username/reponame/", new GithubFullPath("username", "reponame"));
    tests.add("git://github.com/username/reponame/", new GithubFullPath("username", "reponame"));
    tests.add("git@github.com:username/reponame/", new GithubFullPath("username", "reponame"));

    tests.add("https://github.com/username/reponame", new GithubFullPath("username", "reponame"));
    tests.add("https://github.com/username/reponame.git", new GithubFullPath("username", "reponame"));
    tests.add("https://github.com/username/reponame.git/", new GithubFullPath("username", "reponame"));
    tests.add("git@github.com:username/reponame.git/", new GithubFullPath("username", "reponame"));

    tests.add("http://login:passsword@github.com/username/reponame/", new GithubFullPath("username", "reponame"));

    tests.add("HTTPS://GitHub.com/username/reponame/", new GithubFullPath("username", "reponame"));
    tests.add("https://github.com/UserName/RepoName/", new GithubFullPath("UserName", "RepoName"));

    tests.add("https://github.com/RepoName/", null);
    tests.add("git@github.com:user/", null);
    tests.add("https://user:pass@github.com/", null);

    runTestCase(tests, in -> getUserAndRepositoryFromRemoteUrl(in));
  }

  public void testMakeGithubRepoFromRemoteUrl() {
    TestCase<String> tests = new TestCase<>();

    tests.add("http://github.com/username/reponame/", "https://github.com/username/reponame");
    tests.add("https://github.com/username/reponame/", "https://github.com/username/reponame");
    tests.add("git://github.com/username/reponame/", "https://github.com/username/reponame");
    tests.add("git@github.com:username/reponame/", "https://github.com/username/reponame");

    tests.add("https://github.com/username/reponame", "https://github.com/username/reponame");
    tests.add("https://github.com/username/reponame.git", "https://github.com/username/reponame");
    tests.add("https://github.com/username/reponame.git/", "https://github.com/username/reponame");
    tests.add("git@github.com:username/reponame.git/", "https://github.com/username/reponame");

    tests.add("git@github.com:username/reponame/", "https://github.com/username/reponame");
    tests.add("http://login:passsword@github.com/username/reponame/", "https://github.com/username/reponame");

    tests.add("HTTPS://GitHub.com/username/reponame/", "https://github.com/username/reponame");
    tests.add("https://github.com/UserName/RepoName/", "https://github.com/UserName/RepoName");

    tests.add("https://github.com/RepoName/", null);
    tests.add("git@github.com:user/", null);
    tests.add("https://user:pass@github.com/", null);

    runTestCase(tests, in -> makeGithubRepoUrlFromRemoteUrl(in, "https://github.com"));
  }

  public void testGetHostFromUrl() {
    TestCase<String> tests = new TestCase<>();

    tests.add("github.com", "github.com");
    tests.add("api.github.com", "api.github.com");
    tests.add("github.com/", "github.com");
    tests.add("api.github.com/", "api.github.com");

    tests.add("github.com/user/repo/", "github.com");
    tests.add("api.github.com/user/repo/", "api.github.com");

    tests.add("http://github.com/user/repo/", "github.com");
    tests.add("https://github.com/user/repo/", "github.com");
    tests.add("git://github.com/user/repo/", "github.com");
    tests.add("git@github.com:user/repo/", "github.com");

    tests.add("git@github.com:username/repo/", "github.com");
    tests.add("https://username:password@github.com/user/repo/", "github.com");
    tests.add("https://username@github.com/user/repo/", "github.com");
    tests.add("https://github.com:2233/user/repo/", "github.com");

    tests.add("HTTP://GITHUB.com/user/repo/", "GITHUB.com");
    tests.add("HttP://GitHub.com/user/repo/", "GitHub.com");

    runTestCase(tests, in -> getHostFromUrl(in));
  }

  public void testGetApiUrl() {
    TestCase<String> tests = new TestCase<>();

    tests.add("github.com", "https://api.github.com");
    tests.add("https://github.com/", "https://api.github.com");
    tests.add("api.github.com/", "https://api.github.com");

    tests.add("https://my.site.com/", "https://my.site.com/api/v3");
    tests.add("https://api.site.com/", "https://api.site.com/api/v3");
    tests.add("https://url.github.com/", "https://url.github.com/api/v3");

    tests.add("my.site.com/", "https://my.site.com/api/v3");
    tests.add("api.site.com/", "https://api.site.com/api/v3");
    tests.add("url.github.com/", "https://url.github.com/api/v3");

    tests.add("http://my.site.com/", "http://my.site.com/api/v3");
    tests.add("http://api.site.com/", "http://api.site.com/api/v3");
    tests.add("http://url.github.com/", "http://url.github.com/api/v3");

    tests.add("HTTP://GITHUB.com", "http://api.github.com");
    tests.add("HttP://GitHub.com/", "http://api.github.com");

    runTestCase(tests, in -> getApiUrl(in));
  }
}
