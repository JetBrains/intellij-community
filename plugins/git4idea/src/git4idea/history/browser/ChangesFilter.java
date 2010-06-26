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

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AreaMap;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.FilePathsHelper;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairProcessor;
import git4idea.GitUtil;
import org.jetbrains.annotations.NotNull;
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
        if (combiner.accept(filter.getMemoryFilter())) {
          taken = true;
          break;
        }
      }
      if (! taken) {
        result.add(filter.getMemoryFilter());
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

  public interface CommandParametersFilter {
    void applyToCommandLine(final List<String> sink);
  }

  public interface Filter {
    @NotNull
    MemoryFilter getMemoryFilter();
    @Nullable
    CommandParametersFilter getCommandParametersFilter();
  }

  public static class Author implements Filter {
    private final String myRegexp;
    private Pattern myPattern;
    private CommandParametersFilter myCommandParametersFilter;
    private MemoryFilter myMemoryFilter;

    public Author(String regexp) {
      myRegexp = regexp;
      myPattern = Pattern.compile(myRegexp);
      myCommandParametersFilter = new CommandParametersFilter() {
        public void applyToCommandLine(List<String> sink) {
          sink.add("--author=" + myRegexp);
        }
      };
      myMemoryFilter = new MemoryFilter() {
        public boolean applyInMemory(GitCommit commit) {
          return myPattern.matcher(commit.getAuthor()).matches();
        }
      };
    }

    public CommandParametersFilter getCommandParametersFilter() {
      return myCommandParametersFilter;
    }

    @NotNull
    public MemoryFilter getMemoryFilter() {
      return myMemoryFilter;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Author author = (Author)o;

      if (myRegexp != null ? !myRegexp.equals(author.myRegexp) : author.myRegexp != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myRegexp != null ? myRegexp.hashCode() : 0;
    }
  }

  public static class Committer implements Filter {
    private final String myRegexp;
    private Pattern myPattern;
    private MemoryFilter myMemoryFilter;
    private CommandParametersFilter myCommandParametersFilter;

    public Committer(String regexp) {
      myRegexp = regexp;
      myPattern = Pattern.compile(myRegexp);
      myCommandParametersFilter = new CommandParametersFilter() {
        public void applyToCommandLine(List<String> sink) {
          sink.add("--committer=" + myRegexp);
        }
      };
      myMemoryFilter = new MemoryFilter() {
        public boolean applyInMemory(GitCommit commit) {
          return myPattern.matcher(commit.getCommitter()).matches();
        }
      };
    }

    public CommandParametersFilter getCommandParametersFilter() {
      return myCommandParametersFilter;
    }

    @NotNull
    public MemoryFilter getMemoryFilter() {
      return myMemoryFilter;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Committer committer = (Committer)o;

      if (myRegexp != null ? !myRegexp.equals(committer.myRegexp) : committer.myRegexp != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myRegexp != null ? myRegexp.hashCode() : 0;
    }
  }

  public static class BeforeDate implements Filter {
    private final Date myDate;
    private CommandParametersFilter myCommandParametersFilter;
    private MemoryFilter myMemoryFilter;

    public BeforeDate(final Date date) {
      myDate = new Date(date.getTime() + 1);
      myCommandParametersFilter = new CommandParametersFilter() {
        public void applyToCommandLine(List<String> sink) {
          sink.add("--before=" + formatDate(myDate));
        }
      };
      myMemoryFilter = new MemoryFilter() {
        public boolean applyInMemory(GitCommit commit) {
          return commit.getDate().before(myDate);
        }
      };
    }

    public CommandParametersFilter getCommandParametersFilter() {
      return myCommandParametersFilter;
    }

    @NotNull
    public MemoryFilter getMemoryFilter() {
      return myMemoryFilter;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      BeforeDate that = (BeforeDate)o;

      if (myDate != null ? !myDate.equals(that.myDate) : that.myDate != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myDate != null ? myDate.hashCode() : 0;
    }
  }

  public static class AfterDate implements Filter {
    private final Date myDate;
    private CommandParametersFilter myCommandParametersFilter;
    private MemoryFilter myMemoryFilter;

    public AfterDate(final Date date) {
      myDate = new Date(date.getTime() - 1);
      myCommandParametersFilter = new CommandParametersFilter() {
        public void applyToCommandLine(List<String> sink) {
          sink.add("--after=" + formatDate(myDate));
        }
      };
      myMemoryFilter = new MemoryFilter() {
        public boolean applyInMemory(GitCommit commit) {
          return commit.getDate().after(myDate);
        }
      };
    }

    public CommandParametersFilter getCommandParametersFilter() {
      return myCommandParametersFilter;
    }

    @NotNull
    public MemoryFilter getMemoryFilter() {
      return myMemoryFilter;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AfterDate afterDate = (AfterDate)o;

      if (myDate != null ? !myDate.equals(afterDate.myDate) : afterDate.myDate != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myDate != null ? myDate.hashCode() : 0;
    }
  }

  private static String formatDate(final Date date) {
    return GitUtil.gitTime(date);
  }

  public static class StructureFilter implements Filter {
    private final AreaMap<String, VirtualFile> myMap;
    private MemoryFilter myMemoryFilter;

    public StructureFilter() {
      myMap = new AreaMap<String, VirtualFile>(new PairProcessor<String, String>() {
        public boolean process(final String candidate, final String key) {
          return key.startsWith(candidate);
        }
      });
      myMemoryFilter = new MemoryFilter() {
        public boolean applyInMemory(GitCommit commit) {
          if (myMap.isEmpty()) return true;
          
          final List<FilePath> pathList = commit.getPathsList();
          final Ref<Boolean> found = new Ref<Boolean>();

          for (FilePath filePath : pathList) {
            myMap.getSimiliar(FilePathsHelper.convertWithLastSeparator(filePath), new PairProcessor<String, VirtualFile>() {
              public boolean process(String s, VirtualFile virtualFile) {
                found.set(true);
                // take only first.. should be only first
                return true;
              }
            });
            if (Boolean.TRUE.equals(found.get())) break;
          }
          return Boolean.TRUE.equals(found.get());
        }
      };
    }

    public boolean addPath(final VirtualFile vf) {
      final Collection<VirtualFile> filesWeAlreadyHave = myMap.values();
      final Collection<VirtualFile> childrenToRemove = new LinkedList<VirtualFile>();
      for (VirtualFile current : filesWeAlreadyHave) {
        if (current.equals(vf)) return false; // doesnt add exact same
        if (VfsUtil.isAncestor(vf, current, false)) {
          childrenToRemove.add(current);
          continue;
        }
        if (childrenToRemove.isEmpty()) {
          if (VfsUtil.isAncestor(current, vf, false)) {
            return false; // we have a parent already
          }
        }
      }
      for (VirtualFile virtualFile : childrenToRemove) {
        myMap.removeByValue(virtualFile);
      }

      myMap.put(FilePathsHelper.convertWithLastSeparator(vf), vf);
      return true;
    }

    public boolean containsFile(final VirtualFile vf) {
      return myMap.contains(FilePathsHelper.convertWithLastSeparator(vf));
    }

    public void removePath(final VirtualFile vf) {
      myMap.removeByValue(vf);
    }

    public boolean isEmpty() {
      return myMap.isEmpty();
    }

    // can be applied only in memory
    public CommandParametersFilter getCommandParametersFilter() {
      return null;
    }

    @NotNull
    public MemoryFilter getMemoryFilter() {
      return myMemoryFilter;
    }
  }

  public static class Comment implements Filter {
    private final String myRegexp;
    private Pattern myPattern;
    private CommandParametersFilter myCommandParametersFilter;
    private MemoryFilter myMemoryFilter;

    public Comment(final String regexp) {
      myRegexp = regexp;
      myPattern = Pattern.compile(myRegexp);
      myCommandParametersFilter = new CommandParametersFilter() {
        public void applyToCommandLine(List<String> sink) {
          sink.add("--grep=" + myRegexp);
        }
      };
      myMemoryFilter = new MemoryFilter() {
        public boolean applyInMemory(GitCommit commit) {
          return myPattern.matcher(commit.getDescription()).matches() || myPattern.matcher(commit.getCommitter()).matches() ||
                 myPattern.matcher(commit.getAuthor()).matches();
        }
      };
    }

    public CommandParametersFilter getCommandParametersFilter() {
      return myCommandParametersFilter;
    }

    @NotNull
    public MemoryFilter getMemoryFilter() {
      return myMemoryFilter;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Comment comment = (Comment)o;

      if (myRegexp != null ? !myRegexp.equals(comment.myRegexp) : comment.myRegexp != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myRegexp != null ? myRegexp.hashCode() : 0;
    }
  }
}
