package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

/**
 * @author Dmitry Krasilschikov
 */

public class NodeId {

  private final String myFileUrl;
  private final PsiElement myElement;

  /**
   * Indicates in which sub tree element is located (controllers/models/migrations/.. /particular userfolder)
   * It is nesessary for supporting user folders more correctly and prevent dublicating NodeIds
   */
  private final String myLocationRootMark;

  private int hash; // Default cached hash code equals to 0
  @NonNls public static final String CONTROLLERS_SUBTREE = "CONTROLLERS_SUBTREE";
  @NonNls public static final String CONTROLLER_IN_CONTROLLERS_SUBTREE = "CONTROLLER_IN_" + CONTROLLERS_SUBTREE;
  @NonNls public static final String ACTION_IN_CONTROLLERS_SUBTREE = "ACTION_IN_" + CONTROLLERS_SUBTREE;
  @NonNls public static final String DOMAINS_SUBTREE = "DOMAINS_SUBTREE";
  @NonNls public static final String DOMAIN_CLASS_IN_DOMAINS_SUBTREE = "DOMAIN_CLASS_IN_" + DOMAINS_SUBTREE;
  @NonNls public static final String VIEWS_SUBTREE = "VIEWS_SUBTREE";
  @NonNls public static final String TESTS_TREE = "TESTS";
  @NonNls public static final String TEST_CLASS_IN_TESTS_SUBTREE = "TEST_IN" + TESTS_TREE;

  public NodeId(@NotNull final String fileUrl, @Nullable final String locationRootMark) {
    this(fileUrl, null, locationRootMark);
  }

  public NodeId(@NotNull final PsiElement element, @Nullable final String locationRootMark) {
    this(null, element, locationRootMark);
  }

  /**
   * Unique id for Rails Project view nodes.
   * By common sense and for correct support of rename operation.
   * NodeId with equal PsiElements and locationRootMarks should be equal. Thus there is
   * no sence to use psiElement with cached fileUrl
   * On the other hand some elements may not have reperesentaion in Psi
   * (e.g. files with unregistered extension), thus we should work with them via file url.
   * @param fileUrl
   * @param element
   * @param locationRootMark
   */
  private NodeId(@Nullable final String fileUrl,
                 @Nullable final PsiElement element,
                 @Nullable final String locationRootMark) {
    myFileUrl = fileUrl;
    myElement = element;
    myLocationRootMark = locationRootMark;
  }

  @Nullable
  public String getFileUrl() {
    return myFileUrl;
  }

  @Nullable
  public String getLocationRootMark() {
    return myLocationRootMark;
  }

  @Nullable
  public PsiElement getPsiElement() {
    return myElement;
  }

  public String toString() {
    // For Debug purposes

    final StringBuilder buff = new StringBuilder();
    buff.append("[url = ");
    buff.append(getFileUrl());
    buff.append(", psiElement = ");
    buff.append(getPsiElement());
    buff.append(", location mark = (");
    buff.append(getLocationRootMark());
    buff.append(")]");

    return buff.toString();
  }

  @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final NodeId nodeId = (NodeId)o;

    // url
    if (myFileUrl != null
        ? !myFileUrl.equals(nodeId.myFileUrl)
        : nodeId.myFileUrl != null) {
      return false;
    }

    // location
    if (myLocationRootMark != null
          ? !myLocationRootMark.equals(nodeId.myLocationRootMark)
          : nodeId.myLocationRootMark != null) {
      return false;
    }

    // pointer on null/not null
    if (myElement != nodeId.myElement) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    if (hash == 0) {
      hash = (myFileUrl != null) ? myFileUrl.hashCode() : 1;
      hash = 31 * hash + (myLocationRootMark != null ? myLocationRootMark.hashCode() : 0);
      // pointer delegates hashCode() to its element

      /**
       * This hack allows as to ignore strange behavior of equals() and hashcode()
       * in LazySmartPsielementPointer which some times can't compare
       * properly pointers pointing on the same psi element instance
       */
      if (myElement != null) {
        hash = 31 * hash + myElement.hashCode();
      } else {
        hash = 31 * hash;
      }
    }
    return hash;
  }
}