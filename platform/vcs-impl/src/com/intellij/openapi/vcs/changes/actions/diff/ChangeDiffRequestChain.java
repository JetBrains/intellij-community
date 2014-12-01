package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.diff.actions.impl.GoToChangePopupBuilder;
import com.intellij.openapi.util.diff.chains.DiffRequestChain;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ChangeDiffRequestChain extends UserDataHolderBase implements DiffRequestChain, GoToChangePopupBuilder.Chain {
  @NotNull private final List<ChangeDiffRequestPresentable> myRequests;
  private int myIndex;

  public ChangeDiffRequestChain(@NotNull List<ChangeDiffRequestPresentable> requests) {
    myRequests = requests;
  }

  @NotNull
  @Override
  public List<? extends ChangeDiffRequestPresentable> getRequests() {
    return myRequests;
  }

  @Override
  public int getIndex() {
    return myIndex;
  }

  @Override
  public void setIndex(int index) {
    assert index >= 0 && index < myRequests.size();
    myIndex = index;
  }

  @NotNull
  @Override
  public AnAction createGoToChangeAction(@NotNull Consumer<Integer> onSelected) {
    return new ChangeGoToChangePopupAction<ChangeDiffRequestChain>(this, onSelected) {
      @Override
      protected int findSelectedStep(@Nullable Change change) {
        if (change == null) return -1;
        for (int i = 0; i < myRequests.size(); i++) {
          Change c = myRequests.get(i).getChange();
          if (c.equals(change)) return i;
        }
        return -1;
      }

      @NotNull
      @Override
      protected List<Change> getChanges() {
        return ContainerUtil.mapNotNull(myChain.getRequests(), new Function<ChangeDiffRequestPresentable, Change>() {
          @Override
          public Change fun(ChangeDiffRequestPresentable presentable) {
            return presentable.getChange();
          }
        });
      }

      @Nullable
      @Override
      protected Change getCurrentSelection() {
        return myChain.getRequests().get(myChain.getIndex()).getChange();
      }
    };
  }
}
