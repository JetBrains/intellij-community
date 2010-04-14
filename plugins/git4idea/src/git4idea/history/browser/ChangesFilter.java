/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.browser;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

public class ChangesFilter {

  public abstract static class Merger {
    private final Collection<MemoryFilter> myFilters;
    private MemoryFilter myResult;

    protected Merger() {
      myFilters = new LinkedList<MemoryFilter>();
    }

    protected abstract boolean acceptImpl(MemoryFilter filter);
    protected abstract MemoryFilter merge(final Collection<MemoryFilter> filters);

    public boolean accept(final MemoryFilter filter) {
      if (acceptImpl(filter)) {
        myFilters.add(filter);
        return true;
      }
      return false;
    }

    @Nullable
    public MemoryFilter getResult() {
      if (myFilters.isEmpty()) return null;
      return merge(myFilters);
    }
  }

  public static class UsersMerger extends Merger {
    @Override
    protected boolean acceptImpl(MemoryFilter filter) {
      return filter instanceof Author || filter instanceof Committer;
    }

    @Override
    protected MemoryFilter merge(Collection<MemoryFilter> filters) {
      final CompositeUserMemoryFilter result = new CompositeUserMemoryFilter();
      for (MemoryFilter filter : filters) {
        if (filter instanceof Author) {
          result.addUser(((Author) filter).myRegexp);
        } else if (filter instanceof Committer) {
          result.addUser(((Committer) filter).myRegexp);
        }
      }
      return result;
    }
  }

  public static List<MemoryFilter> combineFilters(final Collection<Filter> filters) {
    final Merger[] mergers = {new UsersMerger()};
    if (filters.isEmpty()) return Collections.emptyList();

    final List<MemoryFilter> result = new LinkedList<MemoryFilter>();
    for (Filter filter : filters) {
      boolean taken = false;
      for (Merger combiner : mergers) {
        if (combiner.accept(filter)) {
          taken = true;
          break;
        }
      }
      if (! taken) {
        result.add(filter);
      }
    }
    for (Merger combiner : mergers) {
      final MemoryFilter combined = combiner.getResult();
      if (combined != null) {
        result.add(combined);
      }
    }
    return result;
  }

  private static class CompositeUserMemoryFilter implements MemoryFilter {
    private final Set<String> myUsers;

    private CompositeUserMemoryFilter() {
      myUsers = new HashSet<String>();
    }

    public void addUser(final String name) {
      myUsers.add(name);
    }

    public boolean applyInMemory(GitCommit commit) {
      return myUsers.contains(commit.getCommitter()) || myUsers.contains(commit.getAuthor());
    }
  }

  public interface MemoryFilter {
    boolean applyInMemory(final GitCommit commit);
  }

  public interface Filter extends MemoryFilter {
    void applyToCommandLine(final List<String> sink);
  }

  public static class Branch implements Filter {
    // todo decode into hash
    private Collection<String> myCommitNames;

    public boolean applyInMemory(final GitCommit commit) {
      for (String commitName : myCommitNames) {
        //if (commit.getHash().startsWith(commitName) || )
      }
      return false;
    }

    public void applyToCommandLine(List<String> sink) {
      //To change body of implemented methods use File | Settings | File Templates.
    }
  }

  public static class Author implements Filter {
    private final String myRegexp;
    private Pattern myPattern;

    public Author(String regexp) {
      myRegexp = regexp;
      myPattern = Pattern.compile(myRegexp);
    }

    public boolean applyInMemory(GitCommit commit) {
      return myPattern.matcher(commit.getAuthor()).matches();
    }

    public void applyToCommandLine(List<String> sink) {
      sink.add("--author=" + myRegexp);
    }
  }

  public static class Committer implements Filter {
    private final String myRegexp;
    private Pattern myPattern;

    public Committer(String regexp) {
      myRegexp = regexp;
      myPattern = Pattern.compile(myRegexp);
    }

    public boolean applyInMemory(GitCommit commit) {
      return myPattern.matcher(commit.getCommitter()).matches();
    }

    public void applyToCommandLine(List<String> sink) {
      sink.add("--committer=" + myRegexp);
    }
  }

  public static class BeforeDate implements Filter {
    private final Date myDate;

    public BeforeDate(final Date date) {
      myDate = date;
    }

    public boolean applyInMemory(GitCommit commit) {
      return commit.getDate().before(myDate);
    }

    public void applyToCommandLine(List<String> sink) {
      sink.add("--before=" + formatDate(myDate));
    }
  }

  public static class AfterDate implements Filter {
    private final Date myDate;

    public AfterDate(final Date date) {
      myDate = date;
    }

    public boolean applyInMemory(final GitCommit commit) {
      return commit.getDate().after(myDate);
    }

    public void applyToCommandLine(final List<String> sink) {
      sink.add("--after=" + formatDate(myDate));
    }
  }

  private static String formatDate(final Date date) {
    return date.toString();
  }

  public static class Comment implements Filter {
    private final String myRegexp;
    private Pattern myPattern;

    public Comment(final String regexp) {
      myRegexp = regexp;
      myPattern = Pattern.compile(myRegexp);
    }

    public boolean applyInMemory(final GitCommit commit) {
      return myPattern.matcher(commit.getDescription()).matches() || myPattern.matcher(commit.getCommitter()).matches() ||
             myPattern.matcher(commit.getAuthor()).matches();
    }

    public void applyToCommandLine(final List<String> sink) {
      sink.add("--grep=" + myRegexp);
    }
  }
}
