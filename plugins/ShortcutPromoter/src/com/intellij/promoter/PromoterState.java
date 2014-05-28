package com.intellij.promoter;

/**
 * @author Konstantin Bulenkov
 */
public class PromoterState {
  private int myClicks;

  public int getClicks() {
    return myClicks;
  }

  public void setClicks(int clicks) {
    myClicks = clicks;
  }

  public void incClicks() {
    myClicks++;
  }
}
