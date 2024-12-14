class Diff {
  diff(oldString, newString, options = {}) {
    let callback = options.callback;
    if (typeof options === 'function') {
      callback = options;
      options = {};
    }

    let self = this;

    function done(value) {
      value = self.postProcess(value, options);
      if (callback) {
        setTimeout(function() { callback(value); }, 0);
        return true;
      } else {
        return value;
      }
    }

    oldString = this.castInput(oldString, options);
    newString = this.castInput(newString, options);

    oldString = this.removeEmpty(this.tokenize(oldString, options));
    newString = this.removeEmpty(this.tokenize(newString, options));

    let newLen = newString.length, oldLen = oldString.length;
    let editLength = 1;
    let maxEditLength = newLen + oldLen;
    if(options.maxEditLength != null) {
      maxEditLength = Math.min(maxEditLength, options.maxEditLength);
    }
    const maxExecutionTime = options.timeout ?? Infinity;
    const abortAfterTimestamp = Date.now() + maxExecutionTime;

    let bestPath = [{ oldPos: -1, lastComponent: undefined }];

    let newPos = this.extractCommon(bestPath[0], newString, oldString, 0, options);
    if (bestPath[0].oldPos + 1 >= oldLen && newPos + 1 >= newLen) {
      return done(buildValues(self, bestPath[0].lastComponent, newString, oldString, self.useLongestToken));
    }

    let minDiagonalToConsider = -Infinity, maxDiagonalToConsider = Infinity;

    function execEditLength() {
      for (
        let diagonalPath = Math.max(minDiagonalToConsider, -editLength);
        diagonalPath <= Math.min(maxDiagonalToConsider, editLength);
        diagonalPath += 2
      ) {
        let basePath;
        let removePath = bestPath[diagonalPath - 1],
          addPath = bestPath[diagonalPath + 1];
        if (removePath) {
          bestPath[diagonalPath - 1] = undefined;
        }

        let canAdd = false;
        if (addPath) {
          const addPathNewPos = addPath.oldPos - diagonalPath;
          canAdd = addPath && 0 <= addPathNewPos && addPathNewPos < newLen;
        }

        let canRemove = removePath && removePath.oldPos + 1 < oldLen;
        if (!canAdd && !canRemove) {
          bestPath[diagonalPath] = undefined;
          continue;
        }

        if (!canRemove || (canAdd && removePath.oldPos < addPath.oldPos)) {
          basePath = self.addToPath(addPath, true, false, 0, options);
        } else {
          basePath = self.addToPath(removePath, false, true, 1, options);
        }

        newPos = self.extractCommon(basePath, newString, oldString, diagonalPath, options);

        if (basePath.oldPos + 1 >= oldLen && newPos + 1 >= newLen) {
          return done(buildValues(self, basePath.lastComponent, newString, oldString, self.useLongestToken));
        } else {
          bestPath[diagonalPath] = basePath;
          if (basePath.oldPos + 1 >= oldLen) {
            maxDiagonalToConsider = Math.min(maxDiagonalToConsider, diagonalPath - 1);
          }
          if (newPos + 1 >= newLen) {
            minDiagonalToConsider = Math.max(minDiagonalToConsider, diagonalPath + 1);
          }
        }
      }

      editLength++;
    }

    if (callback) {
      (function exec() {
        setTimeout(function() {
          if (editLength > maxEditLength || Date.now() > abortAfterTimestamp) {
            return callback();
          }

          if (!execEditLength()) {
            exec();
          }
        }, 0);
      }());
    } else {
      while (editLength <= maxEditLength && Date.now() <= abortAfterTimestamp) {
        let ret = execEditLength();
        if (ret) {
          return ret;
        }
      }
    }
  }

  addToPath(path, added, removed, oldPosInc, options) {
    let last = path.lastComponent;
    if (last && !options.oneChangePerToken && last.added === added && last.removed === removed) {
      return {
        oldPos: path.oldPos + oldPosInc,
        lastComponent: {count: last.count + 1, added: added, removed: removed, previousComponent: last.previousComponent }
      };
    } else {
      return {
        oldPos: path.oldPos + oldPosInc,
        lastComponent: {count: 1, added: added, removed: removed, previousComponent: last }
      };
    }
  }

  extractCommon(basePath, newString, oldString, diagonalPath, options) {
    let newLen = newString.length,
      oldLen = oldString.length,
      oldPos = basePath.oldPos,
      newPos = oldPos - diagonalPath,
      commonCount = 0;

    while (newPos + 1 < newLen && oldPos + 1 < oldLen && this.equals(oldString[oldPos + 1], newString[newPos + 1], options)) {
      newPos++;
      oldPos++;
      commonCount++;
      if (options.oneChangePerToken) {
        basePath.lastComponent = {count: 1, previousComponent: basePath.lastComponent, added: false, removed: false};
      }
    }

    if (commonCount && !options.oneChangePerToken) {
      basePath.lastComponent = {count: commonCount, previousComponent: basePath.lastComponent, added: false, removed: false};
    }

    basePath.oldPos = oldPos;
    return newPos;
  }

  baseEquals(left, right, options) {
    if (options.comparator) {
      return options.comparator(left, right);
    } else {
      return left === right || (options.ignoreCase && left.toLowerCase() === right.toLowerCase());
    }
  }

  removeEmpty(array) {
    let ret = [];
    for (let i = 0; i < array.length; i++) {
      if (array[i]) {
        ret.push(array[i]);
      }
    }
    return ret;
  }

  castInput(value) {
    return value;
  }

  tokenize(value) {
    return Array.from(value);
  }

  join(chars) {
    return chars.join('');
  }

  postProcess(changeObjects) {
    return changeObjects;
  }

  tokenize(value, options) {
    if (options && options.stripTrailingCr) {
      value = value.replace(/\r\n/g, '\n');
    }

    let retLines = [],
      linesAndNewlines = value.split(/(\n|\r\n)/);

    if (!linesAndNewlines[linesAndNewlines.length - 1]) {
      linesAndNewlines.pop();
    }

    for (let i = 0; i < linesAndNewlines.length; i++) {
      let line = linesAndNewlines[i];

      if (i % 2 && (!options || !options.newlineIsToken)) {
        retLines[retLines.length - 1] += line;
      } else {
        retLines.push(line);
      }
    }

    return retLines;
  }

  equals(left, right, options) {
    if (options.ignoreWhitespace) {
      if (!options.newlineIsToken || !left.includes('\n')) {
        left = left.trim();
      }
      if (!options.newlineIsToken || !right.includes('\n')) {
        right = right.trim();
      }
    }
    return this.baseEquals(left, right, options);
  }

  diffLines(oldStr, newStr, callback) {
    return this.diff(oldStr, newStr, { callback });
  }

  generateOptions(options, defaults) {
    if (typeof options === 'function') {
      defaults.callback = options;
    } else if (options) {
      for (let name in options) {
        if (options.hasOwnProperty(name)) {
          defaults[name] = options[name];
        }
      }
    }
    return defaults;
  }

  computeDiff(oldCode, newCode) {
    const changes = this.diffLines(oldCode || "", newCode);

    let oldIndex = -1;
    return changes.map(({ value, count, removed, added }) => {
      const lines = value.split(/\r\n|\r|\n/);
      const lastLine = lines.pop();
      if (lastLine) {
        lines.push(lastLine);
      }
      const result = {
        oldIndex,
        lines,
        count,
        removed,
        added
      };
      if (!added) {
        oldIndex += count || 0;
      }
      return result;
    });
  }

  unifiedSlideDiff(prevCode, currCode, slideIndex) {
    const changes = this.computeDiff(prevCode, currCode);
    const unifiedDiff = [];
    let oldLineNumber = 1;
    let newLineNumber = 1;

    changes.forEach(change => {
      const { lines, added, removed } = change;
      lines.forEach(line => {
        if (added) {
          unifiedDiff.push({
            content: `+ ${line}`,
            type: "added",
            oldLineNumber: '',
            newLineNumber: newLineNumber,
            slideIndex
          });
          newLineNumber++;
        } else if (removed) {
          unifiedDiff.push({
            content: `- ${line}`,
            type: "removed",
            oldLineNumber: oldLineNumber,
            newLineNumber: '',
            slideIndex
          });
          oldLineNumber++;
        } else {
          unifiedDiff.push({
            content: `  ${line}`,
            type: "unchanged",
            oldLineNumber: oldLineNumber,
            newLineNumber: newLineNumber,
            slideIndex
          });
          oldLineNumber++;
          newLineNumber++;
        }
      });
    });

    return unifiedDiff;
  }
}

function buildValues(diff, lastComponent, newString, oldString, useLongestToken) {
  const components = [];
  let nextComponent;
  while (lastComponent) {
    components.push(lastComponent);
    nextComponent = lastComponent.previousComponent;
    delete lastComponent.previousComponent;
    lastComponent = nextComponent;
  }
  components.reverse();

  let componentPos = 0,
    componentLen = components.length,
    newPos = 0,
    oldPos = 0;

  for (; componentPos < componentLen; componentPos++) {
    let component = components[componentPos];
    if (!component.removed) {
      if (!component.added && useLongestToken) {
        let value = newString.slice(newPos, newPos + component.count);
        value = value.map(function(value, i) {
          let oldValue = oldString[oldPos + i];
          return oldValue.length > value.length ? oldValue : value;
        });

        component.value = diff.join(value);
      } else {
        component.value = diff.join(newString.slice(newPos, newPos + component.count));
      }
      newPos += component.count;

      if (!component.added) {
        oldPos += component.count;
      }
    } else {
      component.value = diff.join(oldString.slice(oldPos, oldPos + component.count));
      oldPos += component.count;
    }
  }

  return components;
}

class HighlightResistantDiff extends Diff {
  highlighters;

  constructor(highlighters) {
    super();
    this.highlighters = highlighters;
  }

  equals(left, right, options) {
    let indexOf = -1;
    for (const highlighter of this.highlighters) {
      indexOf = right.indexOf(highlighter);
      if (indexOf >= 0) {
        break;
      }
    }

    if (indexOf >= 0) {
      const endsWithNewline = right.endsWith("\n");
      right = right.substring(0, indexOf) + (endsWithNewline ? "\n" : "");
    }
    return super.equals(left, right, options);
  }
}