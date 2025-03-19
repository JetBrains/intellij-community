// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net.ssl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.*;

import java.security.cert.X509Certificate;
import java.util.*;

import static com.intellij.util.net.ssl.CertificateWrapper.CommonField.COMMON_NAME;
import static com.intellij.util.net.ssl.CertificateWrapper.CommonField.ORGANIZATION;

/**
 * @author Mikhail Golubev
 */
@ApiStatus.Internal
public final class CertificateTreeBuilder implements Disposable {
  private static final SimpleTextAttributes STRIKEOUT_ATTRIBUTES = new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, null);
  private static final RootDescriptor ROOT_DESCRIPTOR = new RootDescriptor();

  private final MultiMap<String, CertificateWrapper> myCertificates = new MultiMap<>();
  private final StructureTreeModel<MyTreeStructure> myStructureTreeModel;
  private final Tree myTree;

  private static final Comparator<NodeDescriptor<?>> NODE_COMPARATOR = (o1, o2) -> {
    if (o1 instanceof OrganizationDescriptor && o2 instanceof OrganizationDescriptor) {
      return ((String)o1.getElement()).compareTo((String)o2.getElement());
    }
    else if (o1 instanceof CertificateDescriptor && o2 instanceof CertificateDescriptor) {
      String cn1 = ((CertificateDescriptor)o1).getElement().getSubjectField(COMMON_NAME);
      String cn2 = ((CertificateDescriptor)o2).getElement().getSubjectField(COMMON_NAME);
      return cn1.compareTo(cn2);
    }
    return 0;
  };

  public CertificateTreeBuilder(@NotNull Tree tree) {
    myTree = tree;
    MyTreeStructure treeStructure = new MyTreeStructure();
    myStructureTreeModel = new StructureTreeModel<>(treeStructure, NODE_COMPARATOR, this);
    AsyncTreeModel asyncTreeModel = new AsyncTreeModel(myStructureTreeModel, this);
    tree.setModel(asyncTreeModel);
  }

  public void reset(@NotNull Collection<? extends X509Certificate> certificates) {
    myCertificates.clear();
    for (X509Certificate certificate : certificates) {
      addCertificate(certificate);
    }
    // expand organization nodes at the same time
    //initRootNode();
    myStructureTreeModel.invalidateAsync();
    TreeUtil.expandAll(myTree);
  }

  public void addCertificate(@NotNull X509Certificate certificate) {
    CertificateWrapper wrapper = new CertificateWrapper(certificate);
    myCertificates.putValue(wrapper.getSubjectField(ORGANIZATION), wrapper);
    myStructureTreeModel.invalidateAsync();
  }

  /**
   * Remove specified certificate and corresponding organization, if after removal it contains no certificates.
   */
  public void removeCertificate(@NotNull X509Certificate certificate) {
    CertificateWrapper wrapper = new CertificateWrapper(certificate);
    myCertificates.remove(wrapper.getSubjectField(ORGANIZATION), wrapper);
    myStructureTreeModel.invalidateAsync();
  }

  public @Unmodifiable List<X509Certificate> getCertificates() {
    return ContainerUtil.map(myCertificates.values(), wrapper -> wrapper.getCertificate());
  }

  public boolean isEmpty() {
    return myCertificates.isEmpty();
  }

  public void selectCertificate(@NotNull X509Certificate certificate) {
    myStructureTreeModel.select(new CertificateWrapper(certificate), myTree, path -> {});
  }

  public void selectFirstCertificate() {
    TreeUtil.promiseSelectFirstLeaf(myTree);
  }

  /**
   * Returns certificates selected in the tree. If organization node is selected, all its certificates
   * will be returned.
   *
   * @return - selected certificates
   */
  public @NotNull Set<X509Certificate> getSelectedCertificates(boolean addFromOrganization) {
    Set<X509Certificate> selected = new HashSet<>();
    TreeUtil.collectSelectedUserObjects(myTree).forEach(o -> {
      if (o instanceof CertificateDescriptor) {
        selected.add(((CertificateDescriptor)o).getElement().getCertificate());
      }
      else if (o instanceof OrganizationDescriptor) {
        if (addFromOrganization) {
          selected.addAll(getCertificatesByOrganization(((OrganizationDescriptor)o).getElement()));
        }
      }
      else if (o instanceof RootDescriptor) {
        // nop
      }
      else {
        Logger.getInstance(getClass()).error("Unknown tree node object of type: " + o.getClass().getName());
      }
    });
    return selected;
  }

  public @Nullable X509Certificate getFirstSelectedCertificate(boolean addFromOrganization) {
    Set<X509Certificate> certificates = getSelectedCertificates(addFromOrganization);
    return certificates.isEmpty() ? null : certificates.iterator().next();
  }

  public @Unmodifiable @NotNull List<X509Certificate> getCertificatesByOrganization(@NotNull String organizationName) {
    Collection<CertificateWrapper> wrappers = myCertificates.get(organizationName);
    return extract(wrappers);
  }

  @Override
  public void dispose() {

  }

  private static @Unmodifiable List<X509Certificate> extract(Collection<CertificateWrapper> wrappers) {
    return ContainerUtil.map(wrappers, wrapper -> wrapper.getCertificate());
  }

  final class MyTreeStructure extends AbstractTreeStructure {
    @Override
    public @NotNull Object getRootElement() {
      return RootDescriptor.ROOT;
    }

    @Override
    public Object @NotNull [] getChildElements(@NotNull Object element) {
      if (element == RootDescriptor.ROOT) {
        return ArrayUtilRt.toStringArray(myCertificates.keySet());
      }
      else if (element instanceof String) {
        return ArrayUtil.toObjectArray(myCertificates.get((String)element));
      }
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public @Nullable Object getParentElement(@NotNull Object element) {
      if (element == RootDescriptor.ROOT) {
        return null;
      }
      else if (element instanceof String) {
        return RootDescriptor.ROOT;
      }
      return ((CertificateWrapper)element).getSubjectField(ORGANIZATION);
    }

    @Override
    public @NotNull NodeDescriptor<?> createDescriptor(@NotNull Object element, NodeDescriptor parentDescriptor) {
      if (element == RootDescriptor.ROOT) {
        return ROOT_DESCRIPTOR;
      }
      else if (element instanceof String) {
        return new OrganizationDescriptor(parentDescriptor, (String)element);
      }
      return new CertificateDescriptor(parentDescriptor, (CertificateWrapper)element);
    }


    @Override
    public void commit() {
      // do nothing
    }

    @Override
    public boolean hasSomethingToCommit() {
      return false;
    }
  }

  // Auxiliary node descriptors

  abstract static class MyNodeDescriptor<T> extends PresentableNodeDescriptor<T> {
    private final T myObject;

    MyNodeDescriptor(@Nullable NodeDescriptor parentDescriptor, @NotNull T object) {
      super(null, parentDescriptor);
      myObject = object;
    }

    @Override
    public T getElement() {
      return myObject;
    }
  }

  static final class RootDescriptor extends MyNodeDescriptor<Object> {
    public static final Object ROOT = new Object();

    private RootDescriptor() {
      super(null, ROOT);
    }

    @Override
    protected void update(@NotNull PresentationData presentation) {
      presentation.addText(IdeBundle.message("label.certificate.root"), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  static final class OrganizationDescriptor extends MyNodeDescriptor<@Nls String> {
    private OrganizationDescriptor(@Nullable NodeDescriptor parentDescriptor, @Nls @NotNull String object) {
      super(parentDescriptor, object);
    }

    @Override
    protected void update(@NotNull PresentationData presentation) {
      presentation.addText(getElement(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
  }

  static final class CertificateDescriptor extends MyNodeDescriptor<CertificateWrapper> {
    private CertificateDescriptor(@Nullable NodeDescriptor parentDescriptor, @NotNull CertificateWrapper object) {
      super(parentDescriptor, object);
    }

    @Override
    protected void update(@NotNull PresentationData presentation) {
      CertificateWrapper wrapper = getElement();
      SimpleTextAttributes attr = wrapper.isValid() ? SimpleTextAttributes.REGULAR_ATTRIBUTES : STRIKEOUT_ATTRIBUTES;
      presentation.addText(wrapper.getSubjectField(COMMON_NAME), attr);
    }
  }
}
