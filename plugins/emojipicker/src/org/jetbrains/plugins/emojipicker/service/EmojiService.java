// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.emojipicker.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.util.ResourceUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.CorruptedException;
import com.intellij.util.xml.dom.XmlDomReader;
import com.intellij.util.xml.dom.XmlElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.emojipicker.Emoji;
import org.jetbrains.plugins.emojipicker.EmojiCategory;
import org.jetbrains.plugins.emojipicker.EmojiSearchIndex;
import org.jetbrains.plugins.emojipicker.EmojiSkinTone;
import org.xml.sax.SAXException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.jetbrains.plugins.emojipicker.messages.EmojipickerBundle.message;

@Service
@State(name = "EmojiPickerState", storages = @Storage("emoji.picker.xml"))
public final class EmojiService implements PersistentStateComponent<EmojiService.State> {
  private final List<Emoji> myEmoji;
  private final List<EmojiCategory> myCategories;
  private final EmojiCategory myRecentlyUsedCategory;

  private volatile SearchIndex myEmojiSearchIndex;

  @Nls private final String[] myEmojiNames;
  private volatile EmojiSkinTone mySkinTone;
  private volatile boolean dirty;


  @SuppressWarnings("unchecked")
  private EmojiService() throws Exception {
    myRecentlyUsedCategory = new EmojiCategory("RecentlyUsed", new CopyOnWriteArrayList<>());
    mySkinTone = EmojiSkinTone.NO_TONE;

    Path configDir = PathManager.getConfigDir().resolve("emoji-picker");
    Path serializedEmojiPath = configDir.resolve("emoji");
    Path serializedIndexPath = configDir.resolve("index");
    Path serializedNamesPath = configDir.resolve("names");

    myCategories = new ArrayList<>();
    List<Emoji> emoji;
    SearchIndex searchIndex;
    @Nls String[] emojiNames;
    try {
      try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(serializedEmojiPath))) {
        emoji = (List<Emoji>)in.readObject();
        myCategories.add(myRecentlyUsedCategory);
        myCategories.addAll((List<EmojiCategory>)in.readObject());
      }
      try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(serializedIndexPath))) {
        EmojiSearchIndex index = (EmojiSearchIndex) in.readObject();
        // Empty emoji search index -> corrupted data, rebuild.
        if (index.getTotalEmojiIndices() == 0) throw new CorruptedException(serializedIndexPath);
        searchIndex = new SearchIndex(index);
      }
      try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(serializedNamesPath))) {
        emojiNames = (String[])in.readObject();
      }
    }
    catch (IOException | ClassNotFoundException ignore) {
      Files.createDirectories(configDir);
      EmojiData emojiData = EmojiData.read();
      emoji = emojiData.myEmoji;
      myCategories.clear();
      myCategories.add(myRecentlyUsedCategory);
      myCategories.addAll(emojiData.myCategories);
      @Nls String[] names = emojiNames = new String[emoji.size()];
      CldrData cldr = new CldrData(emojiData);
      final Locale baseLocale = Locale.ENGLISH;
      cldr.read(baseLocale, emojiNames);
      searchIndex = new SearchIndex(cldr.indexTree.buildIndex());

      ProgressManager.getInstance().run(
        new Task.Backgroundable(null, message("message.EmojiPicker.Initializing"), false, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            try {
              try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(serializedEmojiPath, StandardOpenOption.CREATE))) {
                out.writeObject(emojiData.myEmoji);
                out.writeObject(emojiData.myCategories);
              }
              try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(serializedNamesPath, StandardOpenOption.CREATE))) {
                out.writeObject(names);
              }
              indicator.setText(message("message.EmojiPicker.BuildingIndex"));
              indicator.setIndeterminate(false);
              Locale[] locales = Locale.getAvailableLocales();
              double multiplier = 1.0 / (double)locales.length;
              for (int i = 0; i < locales.length; i++) {
                indicator.setFraction((double)i * multiplier);
                if (!locales[i].equals(baseLocale)) cldr.read(locales[i], null);
              }
              indicator.setIndeterminate(true);
              SearchIndex searchIndex = new SearchIndex(cldr.indexTree.buildIndex());
              indicator.setText(message("message.EmojiPicker.SavingIndexData"));
              try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(serializedIndexPath, StandardOpenOption.CREATE))) {
                out.writeObject(searchIndex.myIndex);
              }
              myEmojiSearchIndex = searchIndex;
            }
            catch (IOException e) {
              throw new UncheckedIOException(e);
            }
            catch (SAXException e) {
              throw new RuntimeException(e);
            }
          }
        }
      );
    }
    myEmoji = emoji;
    myEmojiSearchIndex = searchIndex;
    myEmojiNames = emojiNames;
  }

  public List<EmojiCategory> getCategories() {
    return myCategories;
  }

  @Nls
  public String findNameForEmoji(Emoji emoji) {
    return emoji == null ? null : myEmojiNames[emoji.getId()];
  }

  public List<Emoji> findEmojiByPrefix(@NonNls String prefix) {
    return myEmojiSearchIndex.lookupEmoji(prefix);
  }

  public void saveRecentlyUsedEmoji(Emoji emoji) {
    myRecentlyUsedCategory.getEmoji().remove(emoji);
    while (myRecentlyUsedCategory.getEmoji().size() >= 32) {
      myRecentlyUsedCategory.getEmoji().remove(myRecentlyUsedCategory.getEmoji().size() - 1);
    }
    myRecentlyUsedCategory.getEmoji().add(0, emoji);
    dirty = true;
  }

  public EmojiSkinTone getCurrentSkinTone() {
    return mySkinTone;
  }

  public void setCurrentSkinTone(EmojiSkinTone skinTone) {
    mySkinTone = skinTone;
    dirty = true;
  }

  @Override
  public @Nullable State getState() {
    if (!dirty) {
      return null;
    }
    dirty = false;
    return new State(mySkinTone, ContainerUtil.map(myRecentlyUsedCategory.getEmoji(), Emoji::getId));
  }

  @Override
  public void loadState(@NotNull State state) {
    if (state.mySkinTone != null) mySkinTone = state.mySkinTone;
    if (state.myRecentlyUsedEmoji != null && !state.myRecentlyUsedEmoji.isEmpty()) {
      myRecentlyUsedCategory.getEmoji().clear();
      myRecentlyUsedCategory.getEmoji().addAll(ContainerUtil.map(state.myRecentlyUsedEmoji, myEmoji::get));
    }
  }

  public static EmojiService getInstance() {
    return ApplicationManager.getApplication().getService(EmojiService.class);
  }


  static final class State {
    public EmojiSkinTone mySkinTone;
    public List<Integer> myRecentlyUsedEmoji;

    @SuppressWarnings("unused")
    private State() { this(EmojiSkinTone.NO_TONE, List.of()); }

    private State(EmojiSkinTone tone, List<Integer> emoji) {
      mySkinTone = tone;
      myRecentlyUsedEmoji = emoji;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      State state = (State)o;
      return Objects.equals(mySkinTone, state.mySkinTone) && Objects.equals(myRecentlyUsedEmoji, state.myRecentlyUsedEmoji);
    }

    @Override
    public int hashCode() {
      return Objects.hash(mySkinTone, myRecentlyUsedEmoji);
    }
  }

  private final class SearchIndex {
    private final EmojiSearchIndex myIndex;
    private final boolean[] myIdMap;

    private SearchIndex(EmojiSearchIndex index) {
      myIndex = index;
      myIdMap = new boolean[index.getTotalEmojiIndices()];
    }

    synchronized List<Emoji> lookupEmoji(@NonNls String prefix) {
      if (myIndex.lookupIds(myIdMap, prefix.toLowerCase())) {
        List<Emoji> result = new ArrayList<>(100);
        for (int i = 0; i < myIdMap.length; i++) {
          if (myIdMap[i]) result.add(myEmoji.get(i));
        }
        return result;
      }
      else {
        return List.of();
      }
    }
  }


  private static final class EmojiData {
    private final List<Emoji> myEmoji = new ArrayList<>();
    private final List<EmojiCategory> myCategories = new ArrayList<>();
    private final Map<@NonNls String, Integer> indicesMap = new HashMap<>();

    private static EmojiData read() throws IOException {
      Set<@NonNls String> tonedEmojiSet = readToned();
      EmojiData emojiData = new EmojiData();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        Objects.requireNonNull(EmojiService.class.getClassLoader().getResourceAsStream("data/emoji.txt")), StandardCharsets.UTF_8))) {
        EmojiCategory category = null;
        @NonNls String s;
        while ((s = reader.readLine()) != null) {
          if (s.isBlank()) {
            category = null;
          }
          else {
            if (category == null) {
              emojiData.myCategories.add(category = new EmojiCategory(s, new ArrayList<>()));
            }
            else {
              int id = emojiData.myEmoji.size();
              Emoji emoji = new Emoji(id, s, tonedEmojiSet.contains(s));
              emojiData.myEmoji.add(emoji);
              category.getEmoji().add(emoji);
              emojiData.indicesMap.put(s, id);
            }
          }
        }
      }
      return emojiData;
    }

    private static Set<@NonNls String> readToned() throws IOException {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        Objects.requireNonNull(EmojiService.class.getClassLoader().getResourceAsStream("data/toned.txt")), StandardCharsets.UTF_8))) {
        return reader.lines().collect(Collectors.toSet());
      }
    }
  }

  private static final class CldrData {
    private final EmojiData emojiData;
    private final EmojiSearchIndex.PrefixTree indexTree = new EmojiSearchIndex.PrefixTree();

    private CldrData(EmojiData emojiData) {
      this.emojiData = emojiData;
    }

    private void read(@NotNull Locale locale, @Nls String[] names) throws IOException, SAXException {
      @NonNls String l = locale.getLanguage();
      if (!locale.getScript().isEmpty()) {
        l += "_" + locale.getScript();
      }
      if (!locale.getCountry().isEmpty()) {
        l += "_" + locale.getCountry();
      }
      byte[] annotations = ResourceUtil.getResourceAsBytes("data/cldr/annotations/" + l + ".xml", EmojiService.class.getClassLoader());
      if (annotations != null) {
        read(annotations, locale, names);
      }
      byte[] derived = ResourceUtil.getResourceAsBytes("data/cldr/annotationsDerived/" + l + ".xml", EmojiService.class.getClassLoader());
      if (derived != null) {
        read(derived, locale, names);
      }
    }

    private void read(byte[] in, Locale locale, @Nls String[] names) {
      XmlElement document = XmlDomReader.readXmlAsModel(in);
      XmlElement annotationsNode = document.getChild("annotations");
      Iterator<XmlElement> annotations = (annotationsNode != null ? annotationsNode : document).children("annotation").iterator();
      while (annotations.hasNext()) {
        XmlElement node = annotations.next();
        @NonNls String emoji = node.getAttributeValue("cp");
        Integer index = emojiData.indicesMap.get(emoji);
        if (index == null) {
          continue;
        }

        @NonNls String value = Objects.requireNonNull(node.content);
        if (node.getAttributeValue("type") == null) {
          for (String keyword : value.split("\\|")) {
            indexTree.add(keyword.strip().toLowerCase(), index);
          }
        }
        else if (names != null) {
          @Nls String name = value.substring(0, 1).toUpperCase(locale) + value.substring(1);
          names[index] = name;
        }
      }
    }
  }
}
