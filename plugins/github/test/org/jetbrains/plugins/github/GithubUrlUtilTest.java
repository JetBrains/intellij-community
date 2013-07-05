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
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.plugins.github.GithubUrlUtil.isGithubUrl;
import static org.jetbrains.plugins.github.GithubUrlUtil.removeProtocolPrefix;
import static org.jetbrains.plugins.github.GithubUrlUtil.removeTrailingSlash;

/**
 * @author Aleksey Pivovarov
 */
public class GithubUrlUtilTest extends UsefulTestCase {
  private static class TestCase<T> {
    @NotNull final public List<Pair<String, T>> tests = new ArrayList<Pair<String, T>>();

    public void add(String in, T out) {
      tests.add(Pair.create(in, out));
    }

  }

  private static <T> void runTestCase(@NotNull TestCase<T> tests, @NotNull Convertor<String, T> func) {
    for (Pair<String, T> test : tests.tests) {
      assertEquals(test.getFirst(), test.getSecond(), func.convert(test.getFirst()));
    }
  }

  public void testRemoveTrailingSlash() throws Throwable {
    TestCase<String> tests = new TestCase<String>();

    tests.add("http://github.com/", "http://github.com");
    tests.add("http://github.com", "http://github.com");

    tests.add("http://github.com/user/repo/", "http://github.com/user/repo");
    tests.add("http://github.com/user/repo", "http://github.com/user/repo");

    runTestCase(tests, new Convertor<String, String>() {
      @Override
      public String convert(String in) {
        return removeTrailingSlash(in);
      }
    });
  }

  public void testRemoveProtocolPrefix() throws Throwable {
    TestCase<String> tests = new TestCase<String>();

    tests.add("github.com/user/repo/", "github.com/user/repo/");
    tests.add("api.github.com/user/repo/", "api.github.com/user/repo/");

    tests.add("http://github.com/user/repo/", "github.com/user/repo/");
    tests.add("https://github.com/user/repo/", "github.com/user/repo/");
    tests.add("git://github.com/user/repo/", "github.com/user/repo/");
    tests.add("git@github.com/user/repo/", "github.com/user/repo/");

    tests.add("git@github.com:username/user/repo/", "github.com:username/user/repo/");
    tests.add("https://username:password@github.com/user/repo/", "github.com/user/repo/");
    tests.add("https://username@github.com/user/repo/", "github.com/user/repo/");
    tests.add("https://github.com:2233/user/repo/", "github.com:2233/user/repo/");

    tests.add("HTTP://GITHUB.com/user/repo/", "GITHUB.com/user/repo/");
    tests.add("HttP://GITHUB.com/user/repo/", "GITHUB.com/user/repo/");

    runTestCase(tests, new Convertor<String, String>() {
      @Override
      public String convert(String in) {
        return removeProtocolPrefix(in);
      }
    });
  }

  public void testIsGithubUrl() throws Throwable {
    TestCase<Boolean> tests = new TestCase<Boolean>();

    tests.add("http://github.com/user/repo", true);
    tests.add("https://github.com/user/repo", true);
    tests.add("git://github.com/user/repo", true);
    tests.add("git@github.com/user/repo", true);

    tests.add("https://github.com/", true);
    tests.add("github.com", true);

    tests.add("https://user@github.com/user/repo", true);
    tests.add("https://user:password@github.com/user/repo", true);
    tests.add("git@github.com:user/user/repo", true);

    tests.add("https://github.com:2233/", true);

    tests.add("HTTPS://GitHub.com:2233/", true);

    tests.add("google.com", false);
    tests.add("github.com.site.ua", false);
    tests.add("sf@hskfh../.#fwenj 32#$", false);
    tests.add("api.github.com", false);
    tests.add("site.com//github.com", false);

    runTestCase(tests, new Convertor<String, Boolean>() {
      @Override
      public Boolean convert(String in) {
        return isGithubUrl(in, removeTrailingSlash(removeProtocolPrefix("https://github.com/")));
      }
    });
    runTestCase(tests, new Convertor<String, Boolean>() {
      @Override
      public Boolean convert(String in) {
        return isGithubUrl(in, removeTrailingSlash(removeProtocolPrefix("http://GitHub.com")));
      }
    });
  }
}
