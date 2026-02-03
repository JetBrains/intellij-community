class X {
  void foo() {
    boolean completed = JobUtil.invokeConcurrentlyUnderProgress(files, new Processor<VirtualFile>() {
      public boolean process(final VirtualFile vfile) {
        final PsiFile file = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
          public PsiFile compute() {
            return myManager.findFile(vfile);
          }
        });
        if (file != null && !(file instanceof PsiBinaryFile)) {
          file.getViewProvider().getContents(); // load contents outside readaction
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              try {
                PsiElement[] psiRoots = file.getPsiRoots();
                Set<PsiElement> processed = new HashSet<PsiElement>(psiRoots.length * 2, (float)0.5);
                for (PsiElement psiRoot : psiRoots) {
                  if (progress != null) progress.checkCanceled();
                  if (!processed.add(psiRoot)) continue;
                  if (!psiRootProcessor.process(psiRoot)) {
                    canceled.set(true);
                    return;
                  }
                }
                myManager.dropResolveCaches();
              }
              catch (ProcessCanceledException e) {
                canceled.set(true);
                pceThrown.set(true);
              }
            }
          });
        }
        if (progress != null) {
          double fraction = (double)counter.incrementAndGet() / size;
          progress.setFraction(fraction);
        }
        return !canceled.get();
      }
    }, false, progress);

  }
}