import os
from abc import abstractmethod
from typing import List, Optional, Type
from tree_sitter import Language, Parser
from data_processing.datapoint import CodeDataPoint
from data_processing.file_extensions import EXT2LANG, LanguageIds
from data_processing.processors.abstract_processor import ProcessorBase
from data_processing.processors.leading_whitespace_remover import LeadingWhitespaceRemoverBase
PROJECT_ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "../../"))
LIB_PATH = os.path.join(PROJECT_ROOT, "build/my-languages.so")
JS_REPO_PATH = os.path.join(PROJECT_ROOT, "tree-sitter/tree-sitter-javascript")
TS_REPO_PATH = os.path.join(PROJECT_ROOT, "tree-sitter/tree-sitter-typescript/typescript")
TSX_REPO_PATH = os.path.join(PROJECT_ROOT, "tree-sitter/tree-sitter-typescript/tsx")
Language.build_library(⇥LIB_PATH, [JS_REPO_PATH, TS_REPO_PATH, TSX_REPO_PATH],⇤)
JS_LANGUAGE = Language(LIB_PATH, "javascript")
TS_LANGUAGE = Language(LIB_PATH, "typescript")
TSX_LANGUAGE = Language(LIB_PATH, "tsx")
TSX_EXT = ".tsx"
LANG2TREE_SITTER = {LanguageIds.JAVASCRIPT: JS_LANGUAGE, LanguageIds.TYPESCRIPT: TS_LANGUAGE}
class TreeSitterCommentRemoverBase(ProcessorBase):⇥COMMENT_TYPE = "comment"
@abstractmethod
def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥raise NotImplementedError⇤@staticmethod
def _remove_comments(dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥try:⇥code = bytes(dp.content, "utf8")
comment_offsets = TreeSitterCommentRemoverBase.get_comment_offsets(⇥code, TSX_LANGUAGE if dp.extension == TSX_EXT else LANG2TREE_SITTER[EXT2LANG[dp.extension]]⇤)
dp.content = TreeSitterCommentRemoverBase._remove_by_offsets(code, comment_offsets).decode("utf8")
return dp⇤except Exception as e:⇥print(e)
return None⇤⇤@staticmethod
def get_comment_offsets(code, language):⇥parser = Parser()
parser.set_language(language)
comment_offsets = []
TreeSitterCommentRemoverBase.dfs_comment_offsets(code, parser.parse(code).root_node, comment_offsets)
return comment_offsets⇤@classmethod
def dfs_comment_offsets(cls, code, node, comment_offsets):⇥if node.type == cls.COMMENT_TYPE:⇥comment_offsets.append((node.start_byte, node.end_byte))
return⇤for child in node.children:⇥cls.dfs_comment_offsets(code, child, comment_offsets)⇤⇤@staticmethod
def _remove_by_offsets(code, offsets):⇥res = b""
start = 0
for left, right in offsets:⇥res += code[start:left]
start = right⇤res += code[start:]
return res⇤@staticmethod
def is_deterministic() -> bool:⇥return True⇤@staticmethod
def must_be_after() -> List[Type["ProcessorBase"]]:⇥return []⇤@staticmethod
def must_be_before() -> List[Type[ProcessorBase]]:⇥return [LeadingWhitespaceRemoverBase]⇤@staticmethod
def is_slow() -> bool:⇥return False⇤⇤class TreeSitterCommentRemoverTrainData(TreeSitterCommentRemoverBase):⇥def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥return self._remove_comments(dp)⇤⇤class TreeSitterCommentRemoverTrainModel(TreeSitterCommentRemoverBase):⇥def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥return self._remove_comments(dp)⇤⇤class TreeSitterCommentRemoverInference(TreeSitterCommentRemoverBase):⇥def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:⇥return dp⇤⇤