package org.jetbrains.android.spellchecker;

import com.intellij.spellchecker.BundledDictionaryProvider;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidBundledDictionaryProvider implements BundledDictionaryProvider {
  @Override
  public String[] getBundledDictionaries() {
    return new String[] {"android.dic"};
  }
}
