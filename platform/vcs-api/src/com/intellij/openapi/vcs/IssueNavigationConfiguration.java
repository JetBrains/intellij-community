// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.io.URLUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@State(name = "IssueNavigationConfiguration", storages = @Storage("vcs.xml"))
public class IssueNavigationConfiguration extends SimpleModificationTracker
  implements PersistentStateComponent<IssueNavigationConfiguration> {
  private static final Logger LOG = Logger.getInstance(IssueNavigationConfiguration.class);

  public static IssueNavigationConfiguration getInstance(Project project) {
    return project.getService(IssueNavigationConfiguration.class);
  }

  private List<IssueNavigationLink> myLinks = new ArrayList<>();

  public List<IssueNavigationLink> getLinks() {
    return myLinks;
  }

  public void setLinks(final List<? extends IssueNavigationLink> links) {
    myLinks = new ArrayList<>(links);
    incModificationCount();
  }

  @Override
  public IssueNavigationConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull IssueNavigationConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static class LinkMatch implements LinkDescriptor, Comparable {
    private final TextRange myRange;
    private final String myTargetUrl;

    public LinkMatch(final TextRange range, final String targetUrl) {
      myRange = range;
      myTargetUrl = targetUrl;
    }

    @NotNull
    @Override
    public TextRange getRange() {
      return myRange;
    }

    public String getTargetUrl() {
      return myTargetUrl;
    }

    @Override
    public int compareTo(Object o) {
      if (!(o instanceof LinkMatch)) {
        return 0;
      }
      return myRange.getStartOffset() - ((LinkMatch)o).getRange().getStartOffset();
    }
  }

  public List<LinkMatch> findIssueLinks(CharSequence text) {
    final List<LinkMatch> result = new ArrayList<>();
    try {
      for (IssueNavigationLink link : myLinks) {
        findIssueLinkMatches(text, link, result);
      }
      TextRange match;
      int lastOffset = 0;
      while ((match = URLUtil.findUrl(text, lastOffset, text.length())) != null) {
        addMatch(result, match, match.subSequence(text).toString());
        lastOffset = match.getEndOffset();
      }
    }
    catch (ProcessCanceledException e) {
      //skip too long processing completely
    }
    Collections.sort(result);
    return result;
  }

  public static void findIssueLinkMatches(@NotNull CharSequence text,
                                          @NotNull IssueNavigationLink link,
                                          @NotNull List<LinkMatch> result) {
    Pattern issuePattern = link.getIssuePattern();
    Matcher m = issuePattern.matcher(text);
    while (m.find()) {
      try {
        String replacement = issuePattern.matcher(m.group(0)).replaceFirst(link.getLinkRegexp());
        addMatch(result, new TextRange(m.start(), m.end()), replacement);
      }
      catch (Exception e) {
        LOG.debug("Malformed regex replacement. IssueLink: " + link + "; text: " + text, e);
      }
    }
  }

  private static void addMatch(final List<LinkMatch> result, final TextRange range, final String replacement) {
    for (Iterator<LinkMatch> iterator = result.iterator(); iterator.hasNext(); ) {
      LinkMatch oldMatch = iterator.next();
      if (oldMatch.getRange().intersectsStrict(range)) {
        if (oldMatch.getRange().getStartOffset() <= range.getStartOffset() &&
            !oldMatch.getRange().equals(range)) {
          return;
        }
        iterator.remove();
      }
    }
    result.add(new LinkMatch(range, replacement));
  }

  public static void processTextWithLinks(@Nls String text, @NotNull List<? extends LinkMatch> matches,
                                          @NotNull Consumer<? super @Nls String> textConsumer,
                                          @NotNull BiConsumer<? super @Nls String, ? super @NlsSafe String> linkWithTargetConsumer) {
    int pos = 0;
    for (IssueNavigationConfiguration.LinkMatch match : matches) {
      TextRange textRange = match.getRange();
      LOG.assertTrue(pos <= textRange.getStartOffset());
      if (textRange.getStartOffset() > pos) {
        textConsumer.accept(text.substring(pos, textRange.getStartOffset()));
      }
      linkWithTargetConsumer.accept(textRange.substring(text), match.getTargetUrl());
      pos = textRange.getEndOffset();
    }
    if (pos < text.length()) {
      textConsumer.accept(text.substring(pos));
    }
  }
}
