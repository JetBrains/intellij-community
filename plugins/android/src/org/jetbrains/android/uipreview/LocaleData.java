package org.jetbrains.android.uipreview;

/**
* @author Eugene.Kudelevsky
*/
public class LocaleData {
  private final String myLanguage;
  private final String myRegion;
  private final String myPresentation;

  public LocaleData(String language, String region, String presentation) {
    myPresentation = presentation;
    myLanguage = language;
    myRegion = region;
  }

  public String getLanguage() {
    return myLanguage;
  }

  public String getRegion() {
    return myRegion;
  }

  @Override
  public String toString() {
    return myPresentation;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LocaleData myLocale = (LocaleData)o;

    if (myLanguage != null ? !myLanguage.equals(myLocale.myLanguage) : myLocale.myLanguage != null) {
      return false;
    }
    if (myRegion != null ? !myRegion.equals(myLocale.myRegion) : myLocale.myRegion != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myLanguage != null ? myLanguage.hashCode() : 0;
    result = 31 * result + (myRegion != null ? myRegion.hashCode() : 0);
    return result;
  }
}
