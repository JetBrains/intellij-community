override var y on replace old {
  def diff = old - <ref>y;
  if (diff != 0)
    for (node in scene.content)
      node.translateY += diff;
}