package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import java.util.EventListener;

/**
 * User: Dmitry.Krasilschikov
 * Date: 11.01.2008
 */
public interface DynamicPropertyChangeListener extends EventListener {
  /*
   * Change property
   */
   public void dynamicPropertyChange(); 
}
